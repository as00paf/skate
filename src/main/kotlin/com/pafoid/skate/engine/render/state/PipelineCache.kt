package com.pafoid.skate.engine.render.state

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.render.rhi.PipelineState
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiPipeline
import com.pafoid.skate.engine.render.rhi.RhiShader
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import java.util.IdentityHashMap

/**
 * The pipelines of one kind of draw ([state]): one per shader and vertex layout, created on first use and reused
 * every frame (REN-12 step 6). A [Shader] swaps its program on hot reload, so a shader's pipelines are rebuilt when
 * its [Shader.rhi] changes. Lookups allocate nothing.
 */
class PipelineCache(
    val state: PipelineState,
    private val device: () -> RhiDevice = RhiContext::require,
) {
    private class Entry(val program: RhiShader) {
        val byLayout = IdentityHashMap<RhiVertexLayout, RhiPipeline>()
    }

    private val entries = IdentityHashMap<Shader, Entry>()

    /** The pipeline drawing [layout] with [shader]; null once the shader is destroyed. */
    fun get(shader: Shader, layout: RhiVertexLayout): RhiPipeline? {
        val program = shader.rhi ?: return null
        val entry = entries[shader]?.takeIf { it.program === program }
            ?: Entry(program).also { fresh -> entries.put(shader, fresh)?.byLayout?.values?.forEach { it.close() } }
        return entry.byLayout[layout] ?: device().createPipeline(state.desc(program, layout)).also { entry.byLayout[layout] = it }
    }

    fun close() {
        entries.values.forEach { e -> e.byLayout.values.forEach { it.close() } }
        entries.clear()
    }
}
