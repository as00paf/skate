package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PipelineDesc
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiCommandList
import com.pafoid.skate.engine.render.rhi.RhiPipeline
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.RhiTimer
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.ShaderParams
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Topology
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkAttachmentDescription
import org.lwjgl.vulkan.VkAttachmentReference
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderPassBeginInfo
import org.lwjgl.vulkan.VkRenderPassCreateInfo
import org.lwjgl.vulkan.VkSubpassDependency
import org.lwjgl.vulkan.VkSubpassDescription
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import org.lwjgl.vulkan.VkViewport
import org.lwjgl.vulkan.VkWriteDescriptorSet

/** A pipeline description; its Vulkan pipelines are built per render pass on first draw (formats come from the pass). */
internal class VkRhiPipeline(override val desc: PipelineDesc) : RhiPipeline {
    class Built(val handle: Long, val dummyInputs: BooleanArray)
    val built = HashMap<Long, Built>()
    var destroy: (Built) -> Unit = {}

    override fun close() {
        built.values.forEach(destroy)
        built.clear()
    }
}

/**
 * Recorded command list of the offscreen spike (REN-13). Each `beginPass`/`endPass` pair records one command buffer
 * that is submitted and waited for at `endPass`: no frames in flight, so buffer writes between passes are safe.
 * GL-style immediate state (textures per unit, uniform buffers per slot, named parameters) is resolved into one
 * descriptor set per draw, with the parameters and uniform blocks copied into a per-pass uniform ring.
 */
internal class VkCommandList(private val core: VkCore, private val device: VulkanDevice) : RhiCommandList {
    private val cmd: VkCommandBuffer = core.allocateCommandBuffer()
    private val ring = core.hostBuffer(RING_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
    private var ringOffset = 0L
    private val descriptorPool = createDescriptorPool()

    private var target: VkRhiRenderTarget? = null
    private var renderPass = VK_NULL_HANDLE
    private var inPass = false
    private var pipeline: VkRhiPipeline? = null
    private var boundPipeline = VK_NULL_HANDLE
    private var input: VkRhiVertexInput? = null
    private val textures = arrayOfNulls<VkRhiTexture>(32)
    private val uniformBuffers = arrayOfNulls<VkRhiBuffer>(16)
    private val uniformOffsets = LongArray(16)
    private var scissor: IntRect? = null

    override fun beginPass(target: RhiRenderTarget?, layer: Int, clear: ClearValues?, viewport: IntRect?) {
        val t = requireNotNull(target as VkRhiRenderTarget?) { "The Vulkan spike renders offscreen only (no back buffer)" }
        check(!inPass) { "beginPass inside a pass" }
        this.target = t
        renderPass = device.renderPass(t, clear?.color != null, clear?.depth != null)
        ringOffset = 0
        vkResetDescriptorPool(core.device, descriptorPool, 0)
        stackPush().use { s ->
            vk(vkResetCommandBuffer(cmd, 0), "vkResetCommandBuffer")
            vk(vkBeginCommandBuffer(cmd, VkCommandBufferBeginInfo.calloc(s).`sType$Default`()
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "vkBeginCommandBuffer")
            val clears = VkClearValue.calloc(t.colors.size + (if (t.depth != null) 1 else 0), s)
            val color = clear?.color
            for (i in t.colors.indices) if (color != null) clears[i].color().float32(0, color[0]).float32(1, color[1]).float32(2, color[2]).float32(3, color[3])
            if (t.depth != null) clears[t.colors.size].depthStencil().depth(clear?.depth ?: 1f).stencil(0)
            val begin = VkRenderPassBeginInfo.calloc(s).`sType$Default`()
                .renderPass(renderPass).framebuffer(t.framebuffer(renderPass, layer))
                .renderArea { it.offset().set(0, 0); it.extent().set(t.width, t.height) }
                .pClearValues(clears)
            vkCmdBeginRenderPass(cmd, begin, VK_SUBPASS_CONTENTS_INLINE)
            val vp = viewport ?: IntRect(0, 0, t.width, t.height)
            val f = ClipSpace.flippedViewport(vp.x, vp.y, vp.width, vp.height, t.height)
            val viewports = VkViewport.calloc(1, s)
            viewports[0].x(f[0]).y(f[1]).width(f[2]).height(f[3]).minDepth(0f).maxDepth(1f)
            vkCmdSetViewport(cmd, 0, viewports)
        }
        inPass = true
        boundPipeline = VK_NULL_HANDLE
        applyScissor()
    }

    override fun endPass() {
        if (!inPass) return
        vkCmdEndRenderPass(cmd)
        core.submitAndWait(cmd)
        inPass = false
        target = null
    }

    override fun setPipeline(pipeline: RhiPipeline) {
        this.pipeline = pipeline as VkRhiPipeline
    }

    override fun setVertexInput(input: RhiVertexInput) {
        this.input = input as VkRhiVertexInput
    }

    override fun setTexture(slot: Int, texture: RhiTexture?) {
        textures[slot] = texture as VkRhiTexture?
    }

    override fun setUniformBuffer(binding: Int, buffer: RhiBuffer, offset: Long, size: Long) {
        uniformBuffers[binding] = buffer as VkRhiBuffer
        uniformOffsets[binding] = offset
    }

    override fun setParams(params: ShaderParams) {
        // The shader's parameters are read at each draw from its pipeline
    }

    override fun setScissor(rect: IntRect?) {
        scissor = rect
        if (inPass) applyScissor()
    }

    private fun applyScissor() = stackPush().use { s ->
        val t = target ?: return@use
        val r = scissor
        val rects = VkRect2D.calloc(1, s)
        if (r == null) rects[0].offset().set(0, 0) else {
            val c = ClipSpace.scissor(r.x, r.y, r.width, r.height, t.height)
            rects[0].offset().set(maxOf(0, c[0]), maxOf(0, c[1]))
        }
        if (r == null) rects[0].extent().set(t.width, t.height) else rects[0].extent().set(r.width, r.height)
        vkCmdSetScissor(cmd, 0, rects)
    }

    override fun draw(vertexCount: Int, firstVertex: Int, instances: Int) {
        if (!prepareDraw(indexed = false)) return
        vkCmdDraw(cmd, vertexCount, instances, firstVertex, 0)
        EngineStats.drawCalls.incrementAndGet()
    }

    override fun drawIndexed(indexCount: Int, firstIndex: Int, instances: Int) {
        if (!prepareDraw(indexed = true)) return
        vkCmdDrawIndexed(cmd, indexCount, instances, firstIndex, 0, 0)
        EngineStats.drawCalls.incrementAndGet()
    }

    /** Binds pipeline, buffers and this draw's descriptor set; false when there is nothing to draw with. */
    private fun prepareDraw(indexed: Boolean): Boolean {
        check(inPass) { "Draw outside a pass" }
        val p = pipeline ?: return false
        val vi = input ?: return false
        val t = target!!
        val shader = p.desc.shader as VkRhiShader
        val compiled = shader.compiled
        val built = p.built.getOrPut(renderPass) { device.buildPipeline(p, compiled, vi, renderPass, t.colors.size) }
        if (built.handle != boundPipeline) {
            vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, built.handle)
            boundPipeline = built.handle
        }
        stackPush().use { s ->
            val buffers = vi.vertexBuffers.map { it.gpu!!.buffer } + built.dummyInputs.map { if (it) device.dummyIntVertex.buffer else device.dummyFloatVertex.buffer }
            vkCmdBindVertexBuffers(cmd, 0, s.longs(*buffers.toLongArray()), s.callocLong(buffers.size))
            if (indexed) vkCmdBindIndexBuffer(cmd, vi.indexBuffer!!.gpu!!.buffer, 0, VK_INDEX_TYPE_UINT32)
            val set = descriptorSet(s, shader, compiled)
            vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, compiled.pipelineLayout, 0, s.longs(set), null)
        }
        return true
    }

    private fun descriptorSet(s: MemoryStack, shader: VkRhiShader, compiled: VkRhiShader.Compiled): Long {
        val alloc = VkDescriptorSetAllocateInfo.calloc(s).`sType$Default`()
            .descriptorPool(descriptorPool).pSetLayouts(s.longs(compiled.setLayout))
        val p = s.mallocLong(1)
        vk(vkAllocateDescriptorSets(core.device, alloc, p), "vkAllocateDescriptorSets")
        val set = p[0]
        val params = shader.parameters() as VkShaderParams
        val writes = VkWriteDescriptorSet.calloc(compiled.descriptors.size, s)
        compiled.descriptors.forEachIndexed { i, d ->
            val w = writes[i].`sType$Default`().dstSet(set).dstBinding(d.binding).dstArrayElement(0)
                .descriptorType(VkRhiShader.descriptorType(d)).descriptorCount(d.count)
            if (d.kind == SpirvReflection.DescriptorKind.UNIFORM_BUFFER) {
                val size = maxOf(d.size, 16).toLong()
                val offset = if (d.binding == GlslToVulkan.PARAMS_BINDING) {
                    ringCopy(params.block.data, 0, size)
                } else {
                    val slot = shader.blockSlots[d.binding]
                    val buffer = slot?.let { uniformBuffers[it] }
                    if (buffer == null) ringCopy(null, 0, size) else ringCopy(buffer.contents, uniformOffsets[slot].toInt(), size)
                }
                w.pBufferInfo(VkDescriptorBufferInfo.calloc(1, s).also { it[0].buffer(ring.buffer).offset(offset).range(size) })
            } else {
                val units = params.units.of(d.binding)
                val infos = VkDescriptorImageInfo.calloc(d.count, s)
                for (e in 0 until d.count) {
                    val tex = textures.getOrNull(units.getOrElse(e) { 0 })?.takeIf { compatible(it, d) } ?: device.dummyFor(d)
                    infos[e].sampler(tex.vkSampler).imageView(tex.view).imageLayout(tex.sampledLayout)
                }
                w.pImageInfo(infos)
            }
        }
        vkUpdateDescriptorSets(core.device, writes, null)
        return set
    }

    private fun compatible(t: VkRhiTexture, d: SpirvReflection.Descriptor): Boolean {
        val dim = when (t.desc.type) {
            TextureType.TEXTURE_2D -> SpirvReflection.ImageDim.D2
            TextureType.TEXTURE_2D_ARRAY -> SpirvReflection.ImageDim.D2_ARRAY
            TextureType.CUBE -> SpirvReflection.ImageDim.CUBE
        }
        return dim == d.dim && t.isDepth == d.depth && t.vkSampler != VK_NULL_HANDLE
    }

    /** Copies [size] bytes of [src] from [from] (zeros past its end, or all zeros when null) into the ring. */
    private fun ringCopy(src: java.nio.ByteBuffer?, from: Int, size: Long): Long {
        val align = core.minUniformAlignment
        val offset = (ringOffset + align - 1) / align * align
        check(offset + size <= ring.size) { "Uniform ring exhausted" }
        val dst = MemoryUtil.memByteBuffer(ring.address + offset, size.toInt())
        dst.clear()
        MemoryUtil.memSet(ring.address + offset, 0, size)
        if (src != null) {
            val n = minOf(size.toInt(), src.capacity() - from).coerceAtLeast(0)
            for (i in 0 until n) dst.put(i, src.get(from + i))
        }
        ringOffset = offset + size
        return offset
    }

    override fun blit(src: RhiRenderTarget, dst: RhiRenderTarget?, srcRect: IntRect?, dstRect: IntRect?, filter: Filter) =
        throw UnsupportedOperationException("blit is not part of the Vulkan spike (REN-13)")

    override fun beginTimer(timer: RhiTimer) {}
    override fun endTimer(timer: RhiTimer) {}

    private fun createDescriptorPool(): Long = stackPush().use { s ->
        val sizes = VkDescriptorPoolSize.calloc(2, s)
        sizes[0].type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(MAX_SETS * 4)
        sizes[1].type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(MAX_SETS * 16)
        val info = VkDescriptorPoolCreateInfo.calloc(s).`sType$Default`().maxSets(MAX_SETS).pPoolSizes(sizes)
        val p = s.mallocLong(1)
        vk(vkCreateDescriptorPool(core.device, info, null, p), "vkCreateDescriptorPool")
        p[0]
    }

    fun destroy() {
        vkDestroyDescriptorPool(core.device, descriptorPool, null)
        ring.close()
        vkFreeCommandBuffers(core.device, core.commandPool, cmd)
    }

    private companion object {
        const val RING_SIZE = 32L shl 20
        const val MAX_SETS = 4096
    }
}

/** Builds render passes and graphics pipelines for [VulkanDevice] (state translation in [VkTranslate]). */
internal object VkPipelineBuilder {
    /**
     * A render pass whose attachments rest in their sampled layouts between passes: cleared attachments start
     * undefined, loaded ones from the resting layout.
     */
    fun renderPass(core: VkCore, colorFormats: IntArray, depthFormat: Int?, clearColor: Boolean, clearDepth: Boolean): Long = stackPush().use { s ->
        val count = colorFormats.size + (if (depthFormat != null) 1 else 0)
        val attachments = VkAttachmentDescription.calloc(count, s)
        colorFormats.forEachIndexed { i, f ->
            attachments[i].format(f).samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(if (clearColor) VK_ATTACHMENT_LOAD_OP_CLEAR else VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(if (clearColor) VK_IMAGE_LAYOUT_UNDEFINED else VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
        }
        if (depthFormat != null) {
            attachments[colorFormats.size].format(depthFormat).samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(if (clearDepth) VK_ATTACHMENT_LOAD_OP_CLEAR else VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(if (clearDepth) VK_IMAGE_LAYOUT_UNDEFINED else VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
        }
        val colorRefs = VkAttachmentReference.calloc(colorFormats.size, s)
        for (i in colorFormats.indices) colorRefs[i].attachment(i).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
        val subpass = VkSubpassDescription.calloc(1, s)
        subpass[0].pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS).colorAttachmentCount(colorFormats.size).pColorAttachments(colorRefs)
        if (depthFormat != null) {
            subpass[0].pDepthStencilAttachment(
                VkAttachmentReference.calloc(s).attachment(colorFormats.size).layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL),
            )
        }
        val deps = VkSubpassDependency.calloc(2, s)
        deps[0].srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0)
            .srcStageMask(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT).dstStageMask(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT)
            .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_MEMORY_READ_BIT or VK_ACCESS_MEMORY_WRITE_BIT)
        deps[1].srcSubpass(0).dstSubpass(VK_SUBPASS_EXTERNAL)
            .srcStageMask(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT).dstStageMask(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT)
            .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_MEMORY_READ_BIT or VK_ACCESS_MEMORY_WRITE_BIT)
        val info = VkRenderPassCreateInfo.calloc(s).`sType$Default`().pAttachments(attachments).pSubpasses(subpass).pDependencies(deps)
        val p = s.mallocLong(1)
        vk(vkCreateRenderPass(core.device, info, null, p), "vkCreateRenderPass")
        p[0]
    }

    /**
     * The graphics pipeline of [pipeline] for [renderPass]. Shader inputs the vertex layout lacks read a zero
     * stride-0 dummy buffer (GL's disabled-attribute behaviour), appended after the layout's bindings.
     */
    fun build(
        core: VkCore,
        pipeline: VkRhiPipeline,
        compiled: VkRhiShader.Compiled,
        input: VkRhiVertexInput,
        renderPass: Long,
        colorCount: Int,
    ): VkRhiPipeline.Built = stackPush().use { s ->
        val state = pipeline.desc.state
        val stages = VkPipelineShaderStageCreateInfo.calloc(2, s)
        val entry = s.UTF8("main")
        stages[0].`sType$Default`().stage(VK_SHADER_STAGE_VERTEX_BIT).module(compiled.vertexModule).pName(entry)
        stages[1].`sType$Default`().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(compiled.fragmentModule).pName(entry)

        val layout = input.layout.desc
        val provided = layout.bindings.flatMap { b -> b.attributes.map { it.location } }.toSet()
        val missing = compiled.vertexReflection.vertexInputs.filter { it.location !in provided }
        val bindings = VkVertexInputBindingDescription.calloc(layout.bindings.size + missing.size, s)
        val attributeCount = layout.bindings.sumOf { it.attributes.size } + missing.size
        val attributes = VkVertexInputAttributeDescription.calloc(attributeCount, s)
        var a = 0
        layout.bindings.forEachIndexed { b, binding ->
            bindings[b].binding(b).stride(binding.stride)
                .inputRate(if (binding.perInstance) VK_VERTEX_INPUT_RATE_INSTANCE else VK_VERTEX_INPUT_RATE_VERTEX)
            for (attr in binding.attributes) {
                attributes[a++].location(attr.location).binding(b).format(VkTranslate.attributeFormat(attr.type)).offset(attr.offset)
            }
        }
        val dummies = BooleanArray(missing.size)
        missing.forEachIndexed { k, m ->
            val b = layout.bindings.size + k
            val isInt = m.scalar != SpirvReflection.Scalar.FLOAT
            dummies[k] = isInt
            bindings[b].binding(b).stride(0).inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
            attributes[a++].location(m.location).binding(b)
                .format(if (isInt) VK_FORMAT_R32G32B32A32_SINT else VK_FORMAT_R32G32B32A32_SFLOAT).offset(0)
        }
        val vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(s).`sType$Default`()
            .pVertexBindingDescriptions(bindings).pVertexAttributeDescriptions(attributes)
        val assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(s).`sType$Default`().topology(VkTranslate.topology(state.topology))
        val viewport = VkPipelineViewportStateCreateInfo.calloc(s).`sType$Default`().viewportCount(1).scissorCount(1)
        val offset = state.polygonOffset
        val raster = VkPipelineRasterizationStateCreateInfo.calloc(s).`sType$Default`()
            .depthClampEnable(state.depth.clamp && core.depthClamp)
            .polygonMode(VK_POLYGON_MODE_FILL).cullMode(VkTranslate.cullMode(state.cull))
            // The negative viewport height flips Y, which also flips winding: GL's counter-clockwise front faces
            // stay counter-clockwise in framebuffer space.
            .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
            .depthBiasEnable(offset != null).depthBiasSlopeFactor(offset?.factor ?: 0f).depthBiasConstantFactor(offset?.units ?: 0f)
            .lineWidth(if (state.topology == Topology.LINES && core.wideLines) state.lineWidth else 1f)
        val multisample = VkPipelineMultisampleStateCreateInfo.calloc(s).`sType$Default`().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
        val depth = VkPipelineDepthStencilStateCreateInfo.calloc(s).`sType$Default`()
            .depthTestEnable(state.depth.test).depthWriteEnable(state.depth.test && state.depth.write)
            .depthCompareOp(VkTranslate.compareOp(state.depth.func))
        val blend = state.blend
        val blendAttachments = VkPipelineColorBlendAttachmentState.calloc(colorCount, s)
        for (i in 0 until colorCount) {
            blendAttachments[i].blendEnable(blend.enabled)
                .srcColorBlendFactor(VkTranslate.blendFactor(blend.srcRgb)).dstColorBlendFactor(VkTranslate.blendFactor(blend.dstRgb))
                .colorBlendOp(VK_BLEND_OP_ADD)
                .srcAlphaBlendFactor(VkTranslate.blendFactor(blend.srcAlpha)).dstAlphaBlendFactor(VkTranslate.blendFactor(blend.dstAlpha))
                .alphaBlendOp(VK_BLEND_OP_ADD)
                .colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)
        }
        val colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(s).`sType$Default`().pAttachments(blendAttachments)
        val dynamic = VkPipelineDynamicStateCreateInfo.calloc(s).`sType$Default`()
            .pDynamicStates(s.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR))
        val info = VkGraphicsPipelineCreateInfo.calloc(1, s)
        info[0].`sType$Default`().pStages(stages).pVertexInputState(vertexInput).pInputAssemblyState(assembly)
            .pViewportState(viewport).pRasterizationState(raster).pMultisampleState(multisample)
            .pDepthStencilState(depth).pColorBlendState(colorBlend).pDynamicState(dynamic)
            .layout(compiled.pipelineLayout).renderPass(renderPass).subpass(0)
        val p = s.mallocLong(1)
        vk(vkCreateGraphicsPipelines(core.device, VK_NULL_HANDLE, info, null, p), "vkCreateGraphicsPipelines(${pipeline.desc.shader.desc.name})")
        VkRhiPipeline.Built(p[0], dummies)
    }
}
