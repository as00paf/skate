package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.utils.RenderConsts2D.VERTEX_SIZE
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f

/** Draws a [UiDrawList] with the 2D batch shader and a top-left-origin pixel ortho projection. */
class UiBatchRenderer {
    private var vbo: RhiBuffer? = null
    private var ebo: RhiBuffer? = null
    private var input: RhiVertexInput? = null
    private var indexCapacityQuads = 0
    private val projection = Matrix4f()
    private val view = Matrix4f()

    fun render(list: UiDrawList, shader: Shader, screenWidth: Int, screenHeight: Int) {
        if (list.quadCount == 0) return
        ensureBuffers(list.quadCount)
        val vertexInput = input ?: return

        projection.identity().ortho(0f, screenWidth.toFloat(), screenHeight.toFloat(), 0f, -1f, 1f)
        shader.start()
        shader.uploadMat4f(Uniforms.PROJECTION, projection)
        shader.uploadMat4f(Uniforms.VIEW, view.identity())
        shader.uploadIntArray(Uniforms.TEXTURES, SAMPLERS)

        val floats = list.quadCount * 4 * VERTEX_SIZE
        vbo!!.replace(list.vertexData.copyOf(floats))

        val cmd = RhiContext.require().commandList()
        val pipeline = pipelines.get(shader, vertexInput.layout) ?: return
        cmd.setPipeline(pipeline)
        cmd.setVertexInput(vertexInput)
        for (batch in list.batches) {
            batch.texture?.let { cmd.setTexture(0, it.rhi) }
            val s = batch.scissor
            // Target scissor origin is bottom-left
            cmd.setScissor(if (s == null) null else scissorRect(s.x, screenHeight - (s.y + s.h), s.w, s.h))
            cmd.drawIndexed(batch.quadCount * 6, batch.firstQuad * 6)
        }
        cmd.setScissor(null)
    }

    /** Over everything, straight alpha keeping the target opaque ([PipelineStates.UI]). */
    private val pipelines = PipelineCache(PipelineStates.UI)

    private var lastScissor: IntRect? = null

    /** Reuses the last rect when it matches, so a frame's clipped batches rarely allocate. */
    private fun scissorRect(x: Int, y: Int, w: Int, h: Int): IntRect {
        val last = lastScissor
        if (last != null && last.x == x && last.y == y && last.width == w && last.height == h) return last
        return IntRect(x, y, w, h).also { lastScissor = it }
    }

    private fun ensureBuffers(quads: Int) {
        val current = input
        if (current == null) {
            indexCapacityQuads = maxOf(quads, 256)
            val device = RhiContext.require()
            val vertices = device.createBuffer(BufferUsage.VERTEX, BufferUpdate.DYNAMIC, FloatArray(0))
            val indices = device.createBuffer(BufferUsage.INDEX, BufferUpdate.DYNAMIC, quadIndices(indexCapacityQuads))
            vbo = vertices
            ebo = indices
            input = device.createVertexInput(device.createVertexLayout(VertexLayouts.BATCH_2D), listOf(vertices), indices)
        } else if (quads > indexCapacityQuads) {
            indexCapacityQuads = maxOf(quads, indexCapacityQuads * 2, 256)
            ebo!!.replace(quadIndices(indexCapacityQuads))
        }
    }

    fun destroy() {
        input?.close()
        vbo?.close()
        ebo?.close()
        input = null; vbo = null; ebo = null; indexCapacityQuads = 0
        pipelines.close()
    }

    companion object {
        private val SAMPLERS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

        /** Index pattern shared with RenderBatch2D: 3,2,0 / 0,2,1 per quad. */
        fun quadIndices(quads: Int): IntArray {
            val idx = IntArray(quads * 6)
            for (q in 0 until quads) {
                val o = q * 4
                val i = q * 6
                idx[i] = o + 3; idx[i + 1] = o + 2; idx[i + 2] = o
                idx[i + 3] = o; idx[i + 4] = o + 2; idx[i + 5] = o + 1
            }
            return idx
        }
    }
}
