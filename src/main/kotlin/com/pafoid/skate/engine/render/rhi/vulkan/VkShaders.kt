package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.RhiShader
import com.pafoid.skate.engine.render.rhi.ShaderCompileError
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import com.pafoid.skate.engine.render.rhi.ShaderParams
import com.pafoid.skate.engine.render.rhi.ShaderStage
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.shaderc.Shaderc.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import java.nio.ByteOrder

/** GLSL -> SPIR-V with shaderc (Apache-2.0), targeting Vulkan 1.1. */
internal object ShadercCompiler {
    private val compiler: Long by lazy { shaderc_compiler_initialize().also { check(it != 0L) { "shaderc unavailable" } } }

    /** SPIR-V words of [source]; throws [ShaderCompileError] with shaderc's message. */
    fun compile(source: String, vertex: Boolean, name: String): IntArray {
        val options = shaderc_compile_options_initialize()
        try {
            shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_1)
            val kind = if (vertex) shaderc_glsl_vertex_shader else shaderc_glsl_fragment_shader
            val result = shaderc_compile_into_spv(compiler, source, kind, name, "main", options)
            try {
                if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                    throw ShaderCompileError(
                        if (vertex) ShaderStage.VERTEX else ShaderStage.FRAGMENT,
                        shaderc_result_get_error_message(result).orEmpty(),
                    )
                }
                val bytes = shaderc_result_get_bytes(result)!!.order(ByteOrder.LITTLE_ENDIAN)
                return IntArray(bytes.remaining() / 4) { bytes.getInt(it * 4) }
            } finally {
                shaderc_result_release(result)
            }
        } finally {
            shaderc_compile_options_release(options)
        }
    }
}

/**
 * A program on Vulkan: the GL shader sources rewritten by [GlslToVulkan], compiled with shaderc on first use (so
 * shaders a run never draws with are never compiled) and reflected to build one descriptor set layout.
 */
internal class VkRhiShader(private val core: VkCore, override val desc: ShaderDesc) : RhiShader {

    /** Everything derived from the compiled program. */
    inner class Compiled {
        val converted = GlslToVulkan.convert(desc.vertexSource, desc.fragmentSource)
        private val vertexWords = ShadercCompiler.compile(converted.vertex, vertex = true, desc.name)
        private val fragmentWords = ShadercCompiler.compile(converted.fragment, vertex = false, desc.name)
        val vertexReflection = SpirvReflection.of(vertexWords)
        val descriptors: List<SpirvReflection.Descriptor> =
            (vertexReflection.descriptors + SpirvReflection.of(fragmentWords).descriptors)
                .groupBy { it.binding }.values.map { group -> group.maxBy { it.size } }
                .sortedBy { it.binding }
        val paramsDescriptor = descriptors.firstOrNull {
            it.binding == GlslToVulkan.PARAMS_BINDING && it.kind == SpirvReflection.DescriptorKind.UNIFORM_BUFFER
        }
        val vertexModule = module(vertexWords)
        val fragmentModule = module(fragmentWords)
        val setLayout: Long = createSetLayout()
        val pipelineLayout: Long = createPipelineLayout()

        private fun module(words: IntArray): Long = stackPush().use { s ->
            val code = s.malloc(words.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            words.forEach { code.putInt(it) }
            code.flip()
            val info = VkShaderModuleCreateInfo.calloc(s).`sType$Default`().pCode(code)
            val p = s.mallocLong(1)
            vk(vkCreateShaderModule(core.device, info, null, p), "vkCreateShaderModule(${desc.name})")
            p[0]
        }

        private fun createSetLayout(): Long = stackPush().use { s ->
            val bindings = VkDescriptorSetLayoutBinding.calloc(descriptors.size, s)
            descriptors.forEachIndexed { i, d ->
                bindings[i].binding(d.binding).descriptorType(descriptorType(d)).descriptorCount(d.count)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT or VK_SHADER_STAGE_FRAGMENT_BIT)
            }
            val info = VkDescriptorSetLayoutCreateInfo.calloc(s).`sType$Default`().pBindings(bindings)
            val p = s.mallocLong(1)
            vk(vkCreateDescriptorSetLayout(core.device, info, null, p), "vkCreateDescriptorSetLayout")
            p[0]
        }

        private fun createPipelineLayout(): Long = stackPush().use { s ->
            val info = VkPipelineLayoutCreateInfo.calloc(s).`sType$Default`().pSetLayouts(s.longs(setLayout))
            val p = s.mallocLong(1)
            vk(vkCreatePipelineLayout(core.device, info, null, p), "vkCreatePipelineLayout")
            p[0]
        }

        fun destroy() {
            vkDestroyPipelineLayout(core.device, pipelineLayout, null)
            vkDestroyDescriptorSetLayout(core.device, setLayout, null)
            vkDestroyShaderModule(core.device, vertexModule, null)
            vkDestroyShaderModule(core.device, fragmentModule, null)
        }
    }

    private var compiledOrNull: Compiled? = null
    val compiled: Compiled get() = compiledOrNull ?: Compiled().also { compiledOrNull = it }

    /** Engine uniform-buffer slot ([bindUniformBlock]) of each block binding. */
    val blockSlots = HashMap<Int, Int>()

    private val params = VkShaderParams(this)

    override fun parameters(): ShaderParams = params

    override fun bindUniformBlock(blockName: String, binding: Int): Boolean {
        val block = compiled.descriptors.firstOrNull {
            it.kind == SpirvReflection.DescriptorKind.UNIFORM_BUFFER && it.name == blockName
        } ?: return false
        blockSlots[block.binding] = binding
        return true
    }

    override fun close() {
        compiledOrNull?.destroy()
        compiledOrNull = null
    }

    companion object {
        fun descriptorType(d: SpirvReflection.Descriptor) = when (d.kind) {
            SpirvReflection.DescriptorKind.UNIFORM_BUFFER -> VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
            SpirvReflection.DescriptorKind.COMBINED_IMAGE_SAMPLER -> VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
        }
    }
}

/**
 * Named parameters of a [VkRhiShader]: plain values go to its [ParamsBlock]; a sampler's value is its texture unit
 * (GL semantics), kept per binding element for the draw's descriptor writes ([SamplerUnits]).
 */
internal class VkShaderParams(private val shader: VkRhiShader) : ShaderParams {
    private var blockOrNull: ParamsBlock? = null
    private var unitsOrNull: SamplerUnits? = null

    val block: ParamsBlock
        get() = blockOrNull ?: shader.compiled.let { c ->
            val d = c.paramsDescriptor
            ParamsBlock(d?.size ?: 0, d?.members ?: emptyList()).also { blockOrNull = it }
        }

    val units: SamplerUnits
        get() = unitsOrNull ?: SamplerUnits(shader.compiled.descriptors).also { unitsOrNull = it }

    override fun set(name: String, value: Float) = block.set(name, value)
    override fun set(name: String, value: Int) {
        if (!units.set(name, value)) block.set(name, value)
    }
    override fun set(name: String, x: Float, y: Float) = block.set(name, 2, x, y, 0f, 0f)
    override fun set(name: String, x: Float, y: Float, z: Float) = block.set(name, 3, x, y, z, 0f)
    override fun set(name: String, x: Float, y: Float, z: Float, w: Float) = block.set(name, 4, x, y, z, w)
    override fun set(name: String, values: IntArray) {
        if (!units.set(name, values)) block.set(name, values)
    }
    override fun setMatrix4(name: String, values: FloatArray) = block.setMatrix4(name, values)
}

/**
 * Texture unit of each combined-image-sampler element, set by name like a GL sampler uniform (default unit 0).
 * Pure Kotlin, unit-tested.
 */
class SamplerUnits(descriptors: List<SpirvReflection.Descriptor>) {
    private val samplers = descriptors.filter { it.kind == SpirvReflection.DescriptorKind.COMBINED_IMAGE_SAMPLER }
    private val byName = samplers.associateBy { it.name }
    private val units = samplers.associate { it.binding to IntArray(it.count) }

    /** Units of binding [binding], one per array element. */
    fun of(binding: Int): IntArray = units[binding] ?: IntArray(0)

    /** Sets `name` or `name[i]`; false when [name] is not a sampler. */
    fun set(name: String, unit: Int): Boolean {
        val bracket = name.indexOf('[')
        val d = byName[if (bracket < 0) name else name.substring(0, bracket)] ?: return false
        val index = if (bracket < 0) 0 else name.substring(bracket + 1, name.indexOf(']')).toIntOrNull() ?: return false
        units.getValue(d.binding).let { if (index in it.indices) it[index] = unit }
        return true
    }

    fun set(name: String, values: IntArray): Boolean {
        val d = byName[name.substringBefore('[')] ?: return false
        val target = units.getValue(d.binding)
        values.copyInto(target, 0, 0, minOf(values.size, target.size))
        return true
    }
}
