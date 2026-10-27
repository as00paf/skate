package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.utils.RenderConsts2D.COLOR_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts2D.COLOR_SIZE
import com.pafoid.skate.engine.utils.RenderConsts2D.ENTITY_ID_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts2D.ENTITY_ID_SIZE
import com.pafoid.skate.engine.utils.RenderConsts2D.POS_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts2D.POS_SIZE
import com.pafoid.skate.engine.utils.RenderConsts2D.TEX_COORDS_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts2D.TEX_COORDS_SIZE
import com.pafoid.skate.engine.utils.RenderConsts2D.TEX_ID_OFFSET
import com.pafoid.skate.engine.utils.RenderConsts2D.TEX_ID_SIZE
import com.pafoid.skate.engine.utils.RenderConsts2D.VERTEX_SIZE
import com.pafoid.skate.engine.utils.RenderConsts2D.VERTEX_SIZE_BYTES
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.state.GLBindings
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glScissor
import org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL30.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL30.GL_ELEMENT_ARRAY_BUFFER
import org.lwjgl.opengl.GL30.GL_FLOAT
import org.lwjgl.opengl.GL30.GL_TRIANGLES
import org.lwjgl.opengl.GL30.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL30.glBindBuffer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glBufferData
import org.lwjgl.opengl.GL30.glDeleteBuffers
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glDrawElements
import org.lwjgl.opengl.GL30.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.glGenBuffers
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.opengl.GL30.glVertexAttribPointer

/** Draws a [UiDrawList] with the 2D batch shader and a top-left-origin pixel ortho projection. */
class UiBatchRenderer {
    private var vao = 0
    private var vbo = 0
    private var ebo = 0
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
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val floats = list.quadCount * 4 * VERTEX_SIZE
        glBufferData(GL_ARRAY_BUFFER, list.vertexData.copyOf(floats), GL_DYNAMIC_DRAW)

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
        if (vao == 0) {
            vao = glGenVertexArrays()
            vbo = glGenBuffers()
            ebo = glGenBuffers()
            glBindVertexArray(vao)
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            attrib(0, POS_SIZE, POS_OFFSET)
            attrib(1, COLOR_SIZE, COLOR_OFFSET)
            attrib(2, TEX_COORDS_SIZE, TEX_COORDS_OFFSET)
            attrib(3, TEX_ID_SIZE, TEX_ID_OFFSET)
            attrib(4, ENTITY_ID_SIZE, ENTITY_ID_OFFSET)
        }
        if (quads > indexCapacityQuads) {
            indexCapacityQuads = maxOf(quads, indexCapacityQuads * 2, 256)
            glBindVertexArray(vao)
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, quadIndices(indexCapacityQuads), GL_DYNAMIC_DRAW)
        }
    }

    private fun attrib(index: Int, size: Int, offset: Int) {
        glVertexAttribPointer(index, size, GL_FLOAT, false, VERTEX_SIZE_BYTES, offset.toLong())
        glEnableVertexAttribArray(index)
    }

    fun destroy() {
        if (vao != 0) glDeleteVertexArrays(vao)
        if (vbo != 0) glDeleteBuffers(vbo)
        if (ebo != 0) glDeleteBuffers(ebo)
        vao = 0; vbo = 0; ebo = 0; indexCapacityQuads = 0
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
