package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiCommandList
import org.lwjgl.opengl.GL30.glBindBufferBase
import org.lwjgl.opengl.GL30.glBindBufferRange
import org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER

/** Immediate command list: GL executes each call at once, so there is nothing to record or submit. */
internal object GlCommandList : RhiCommandList {
    override fun setUniformBuffer(binding: Int, buffer: RhiBuffer, offset: Long, size: Long) {
        val id = (buffer as GlBuffer).id
        if (offset == 0L && size < 0) glBindBufferBase(GL_UNIFORM_BUFFER, binding, id)
        else glBindBufferRange(GL_UNIFORM_BUFFER, binding, id, offset, if (size < 0) buffer.size - offset else size)
    }
}
