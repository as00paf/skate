package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.shader.ShaderFeature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Every engine shader survives the Vulkan rewrite: shaderc (no GPU needed) compiles both stages and the reflected
 * Params block holds every loose uniform, so each named parameter the engine sets has an offset (REN-13).
 */
class EngineShadersVulkanTest {

    private fun shaderc(): Boolean = runCatching { ShadercCompiler.compile("#version 450\nvoid main() {}", vertex = false, "probe") }.isSuccess

    @Test
    fun EveryEngineShader_CompilesForVulkan_AndReflectsItsParams() {
        assumeTrue(shaderc(), "shaderc native library unavailable")
        val device = FakeRhiDevice()
        val loader = ShaderLoader(device = { device })
        val files = File("assets/Shaders").listFiles { f -> f.extension == "glsl" && f.readText().contains("#type") }!!
        assertTrue(files.size >= 15, "found ${files.size} shaders")
        for (f in files.sortedBy { it.name }) loader.loadShader("/shaders/${f.name}")
        loader.loadShader("/shaders/shadow.glsl", setOf(ShaderFeature.ALPHA_TEST.name))

        for (shader in device.shaders) {
            val name = shader.desc.name
            val converted = GlslToVulkan.convert(shader.desc.vertexSource, shader.desc.fragmentSource)
            val vertex = SpirvReflection.of(ShadercCompiler.compile(converted.vertex, vertex = true, name))
            val fragment = SpirvReflection.of(ShadercCompiler.compile(converted.fragment, vertex = false, name))
            val params = (vertex.descriptors + fragment.descriptors).firstOrNull { it.binding == GlslToVulkan.PARAMS_BINDING }
            val declared = converted.params.map { it.substringAfter(' ').substringBefore('[').trim() }.toSet()
            // A Params member is kept by glslang when a stage uses it; both stages together use every one
            val reflected = params?.members?.map { it.name }?.toSet().orEmpty()
            assertEquals(declared, declared intersect reflected, "$name: Params members missing from the reflection")
            for ((sampler, binding) in converted.samplers) {
                val d = (vertex.descriptors + fragment.descriptors).firstOrNull { it.binding == binding }
                assertTrue(d == null || d.name == sampler, "$name: binding $binding is ${d?.name}, expected $sampler")
            }
        }
    }
}
