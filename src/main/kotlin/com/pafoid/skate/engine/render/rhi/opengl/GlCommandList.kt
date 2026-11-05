package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiCommandList
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.rhi.RhiPipeline
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.RhiTimer
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.ShaderParams
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glClearColor
import org.lwjgl.opengl.GL11.glClearDepth
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL31.glDrawArraysInstanced
import org.lwjgl.opengl.GL31.glDrawElementsInstanced
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.glBindBufferBase
import org.lwjgl.opengl.GL30.glBindBufferRange
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBlitFramebuffer
import org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER

/** Immediate command list: GL executes each call at once, so there is nothing to record or submit. */
internal object GlCommandList : RhiCommandList {
    /** Last `glClearDepth` value (GL's default is 1). */
    private var clearDepth = 1f

    /** Primitive mode of the bound pipeline. */
    private var mode = GL_TRIANGLES

    override fun setPipeline(pipeline: RhiPipeline) {
        val p = pipeline as GlPipeline
        GlPrograms.use(p.program)
        GlState.cache.apply(p.desc.state)
        mode = p.mode
    }

    override fun setVertexInput(input: RhiVertexInput) {
        GLBindings.cache.bindVao((input as GlVertexInput).vao)
    }

    override fun setTexture(slot: Int, texture: RhiTexture?) {
        if (texture == null) {
            GLBindings.cache.bindTexture(slot, 0, GLBindings.KIND_2D)
            return
        }
        val kind = when (texture.desc.type) {
            TextureType.TEXTURE_2D -> GLBindings.KIND_2D
            TextureType.TEXTURE_2D_ARRAY -> GLBindings.KIND_2D_ARRAY
            TextureType.CUBE -> GLBindings.KIND_CUBE
        }
        GLBindings.cache.bindTexture(slot, (texture as GlTexture).id, kind)
    }

    override fun setScissor(rect: IntRect?) {
        if (rect == null) GlState.cache.disableScissor()
        else GlState.cache.setScissor(rect.x, rect.y, rect.width, rect.height)
    }

    override fun draw(vertexCount: Int, firstVertex: Int, instances: Int) {
        if (instances == 1) glDrawArrays(mode, firstVertex, vertexCount)
        else glDrawArraysInstanced(mode, firstVertex, vertexCount, instances)
        EngineStats.drawCalls.incrementAndGet()
    }

    override fun drawIndexed(indexCount: Int, firstIndex: Int, instances: Int) {
        val offset = firstIndex.toLong() * Int.SIZE_BYTES
        if (instances == 1) glDrawElements(mode, indexCount, GL_UNSIGNED_INT, offset)
        else glDrawElementsInstanced(mode, indexCount, GL_UNSIGNED_INT, offset, instances)
        EngineStats.drawCalls.incrementAndGet()
    }

    override fun setUniformBuffer(binding: Int, buffer: RhiBuffer, offset: Long, size: Long) {
        val id = (buffer as GlBuffer).id
        if (offset == 0L && size < 0) glBindBufferBase(GL_UNIFORM_BUFFER, binding, id)
        else glBindBufferRange(GL_UNIFORM_BUFFER, binding, id, offset, if (size < 0) buffer.size - offset else size)
    }

    override fun setParams(params: ShaderParams) = GlPrograms.use((params as GlShaderParams).program)

    override fun beginTimer(timer: RhiTimer) {
        (timer as? GlTimer)?.begin()
    }

    override fun endTimer(timer: RhiTimer) {
        (timer as? GlTimer)?.end()
    }

    override fun beginPass(target: RhiRenderTarget?, layer: Int, clear: ClearValues?, viewport: IntRect?) {
        val gl = target as GlRenderTarget?
        glBindFramebuffer(GL_FRAMEBUFFER, gl?.fbo ?: 0)
        gl?.attachLayer(layer)
        when {
            viewport != null -> glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
            gl != null -> glViewport(0, 0, gl.width, gl.height)
            else -> throw IllegalArgumentException("A back buffer pass needs a viewport")
        }
        if (clear != null) clear(clear)
    }

    /** Clears like a Vulkan load op: whole attachment, whatever the scissor and depth-write state. */
    private fun clear(clear: ClearValues) {
        val color = clear.color
        val depth = clear.depth
        var mask = 0
        if (color != null) {
            glClearColor(color[0], color[1], color[2], color[3])
            mask = mask or GL_COLOR_BUFFER_BIT
        }
        if (depth != null) {
            if (depth != clearDepth) {
                glClearDepth(depth.toDouble())
                clearDepth = depth
            }
            // Depth writes stay on afterwards: every draw sets its own with its pipeline
            GlState.cache.setDepthWrite(true)
            mask = mask or GL_DEPTH_BUFFER_BIT
        }
        if (mask == 0) return
        GlState.cache.disableScissor()
        glClear(mask)
    }

    override fun endPass() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    override fun blit(src: RhiRenderTarget, dst: RhiRenderTarget?, srcRect: IntRect?, dstRect: IntRect?, filter: Filter) {
        val from = src as GlRenderTarget
        val to = dst as GlRenderTarget?
        require(dstRect != null || to != null) { "A blit to the back buffer needs a destination rect" }
        val glFilter = when (filter) {
            Filter.NEAREST -> GL_NEAREST
            Filter.LINEAR -> GL_LINEAR
            else -> throw IllegalArgumentException("Blits filter with NEAREST or LINEAR, not $filter")
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, from.fbo)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, to?.fbo ?: 0)
        glBlitFramebuffer(
            srcRect?.x ?: 0, srcRect?.y ?: 0,
            srcRect?.let { it.x + it.width } ?: from.width, srcRect?.let { it.y + it.height } ?: from.height,
            dstRect?.x ?: 0, dstRect?.y ?: 0,
            dstRect?.let { it.x + it.width } ?: to!!.width, dstRect?.let { it.y + it.height } ?: to!!.height,
            GL_COLOR_BUFFER_BIT, glFilter,
        )
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }
}
