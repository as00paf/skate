package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.PipelineDesc
import com.pafoid.skate.engine.render.rhi.RhiPipeline

/** A pipeline is a program plus a [GlStateCache] target on GL; nothing to create or free. */
internal class GlPipeline(override val desc: PipelineDesc) : RhiPipeline {
    val program: Int = (desc.shader as GlShader).program
    val mode: Int = GlFormats.topology(desc.state.topology)

    override fun close() {}
}
