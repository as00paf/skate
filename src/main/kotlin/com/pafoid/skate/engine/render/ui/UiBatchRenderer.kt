package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.utils.RenderConsts2D.VERTEX_SIZE
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.rhi.opengl.GLBindings
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glScissor
import org.lwjgl.opengl.GL30.GL_TRIANGLES
import org.lwjgl.opengl.GL30.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDrawElements

/** Draws a [UiDrawList] with the 2D batch shader and a top-left-origin pixel ortho projection. */
class UiBatchRenderer {
    private var vbo: RhiBuffer? = null
    private var ebo: RhiBuffer? = null
    private var input: RhiVertexInput? = null
    private val vao: Int get() = input?.nativeHandle?.toInt() ?: 0
    private var indexCapacityQuads = 0
    private val projection = Matrix4f()
    private val view = Matrix4f()

    fun render(list: UiDrawList, shader: Shader, screenWidth: Int, screenHeight: Int) {
        if (list.quadCount == 0) return
        ensureBuffers(list.quadCount)

        projection.identity().ortho(0f, screenWidth.toFloat(), screenHeight.toFloat(), 0f, -1f, 1f)
        shader.start()
        shader.uploadMat4f(Uniforms.PROJECTION, projection)
        shader.uploadMat4f(Uniforms.VIEW, view.identity())
        shader.uploadIntArray(Uniforms.TEXTURES, intArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

        glBindVertexArray(vao)
        val floats = list.quadCount * 4 * VERTEX_SIZE
        vbo!!.replace(list.vertexData.copyOf(floats))

        for (batch in list.batches) {
            batch.texture?.let { GLBindings.cache.bindTexture(0, it.texId) }
            val s = batch.scissor
            if (s != null) {
                glEnable(GL_SCISSOR_TEST)
                // GL scissor origin is bottom-left
                glScissor(s.x, screenHeight - (s.y + s.h), s.w, s.h)
            } else {
                glDisable(GL_SCISSOR_TEST)
            }
            glDrawElements(GL_TRIANGLES, batch.quadCount * 6, GL_UNSIGNED_INT, batch.firstQuad * 6L * Int.SIZE_BYTES)
            EngineStats.drawCalls.incrementAndGet()
        }
        glDisable(GL_SCISSOR_TEST)
        glBindVertexArray(0)
        shader.stop()
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
    }

    companion object {
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
