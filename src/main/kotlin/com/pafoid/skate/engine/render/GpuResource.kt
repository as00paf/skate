package com.pafoid.skate.engine.render

import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glDeleteVertexArrays

/**
 * Owner of one GL object id. [close] deletes it exactly once; further calls are no-ops.
 * The deleter is injectable so lifetime logic can be tested without a GL context.
 */
class GpuResource(val id: Int, private val deleter: (Int) -> Unit) : AutoCloseable {
    var isClosed = false
        private set

    override fun close() {
        if (isClosed) return
        isClosed = true
        deleter(id)
    }

    companion object {
        fun buffer(id: Int) = GpuResource(id) { glDeleteBuffers(it) }
        fun vertexArray(id: Int) = GpuResource(id) { glDeleteVertexArrays(it) }
        fun texture(id: Int) = GpuResource(id) { glDeleteTextures(it) }
        fun framebuffer(id: Int) = GpuResource(id) { glDeleteFramebuffers(it) }
    }
}
