package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glBufferSubData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * A GL buffer object. Uploads bind it to a target that is not vertex-array state: index buffers go through
 * `GL_COPY_WRITE_BUFFER`, because binding `GL_ELEMENT_ARRAY_BUFFER` would rebind the index buffer of whatever VAO is
 * bound at that moment.
 */
internal class GlBuffer private constructor(
    override val usage: BufferUsage,
    override val update: BufferUpdate,
    size: Long,
) : RhiBuffer {
    val id: Int = glGenBuffers()
    private val target = GlFormats.uploadTarget(usage)
    private val glUsage = GlFormats.usage(update)

    override var size: Long = size
        private set

    private var closed = false

    private fun bind() = glBindBuffer(target, id)

    override fun update(offset: Long, data: FloatArray) {
        bind(); glBufferSubData(target, offset, data)
    }

    override fun update(offset: Long, data: FloatBuffer) {
        bind(); glBufferSubData(target, offset, data)
    }

    override fun update(offset: Long, data: ByteBuffer) {
        bind(); glBufferSubData(target, offset, data)
    }

    override fun resize(size: Long) {
        bind(); glBufferData(target, size, glUsage)
        this.size = size
    }

    override fun replace(data: FloatArray) {
        bind(); glBufferData(target, data, glUsage)
        size = data.size.toLong() * Float.SIZE_BYTES
    }

    override fun replace(data: IntArray) {
        bind(); glBufferData(target, data, glUsage)
        size = data.size.toLong() * Int.SIZE_BYTES
    }

    override fun close() {
        if (closed) return
        closed = true
        glDeleteBuffers(id)
    }

    companion object {
        fun create(usage: BufferUsage, update: BufferUpdate, size: Long) =
            GlBuffer(usage, update, size).also { it.resize(size) }

        fun create(usage: BufferUsage, update: BufferUpdate, data: FloatArray) =
            GlBuffer(usage, update, 0).also { it.replace(data) }

        fun create(usage: BufferUsage, update: BufferUpdate, data: IntArray) =
            GlBuffer(usage, update, 0).also { it.replace(data) }
    }
}
