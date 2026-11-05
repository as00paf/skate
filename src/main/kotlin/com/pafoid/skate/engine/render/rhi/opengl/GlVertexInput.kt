package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import com.pafoid.skate.engine.render.rhi.VertexLayoutDesc
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_INT
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.opengl.GL30.glVertexAttribIPointer
import org.lwjgl.opengl.GL33.glVertexAttribDivisor

/** GL has no layout object: the description is applied when a [GlVertexInput] (VAO) is built. */
internal class GlVertexLayout(override val desc: VertexLayoutDesc) : RhiVertexLayout {
    val pointers: List<GlAttribPointer> = GlAttribPointer.of(desc)
    override fun close() {}
}

/** One `glVertexAttrib(I)Pointer` call, derived from a [VertexLayoutDesc]; pure for tests. */
internal data class GlAttribPointer(
    val binding: Int,
    val location: Int,
    val components: Int,
    val glType: Int,
    val integer: Boolean,
    val stride: Int,
    val offset: Long,
    val divisor: Int,
) {
    companion object {
        fun of(desc: VertexLayoutDesc): List<GlAttribPointer> = desc.bindings.flatMapIndexed { b, binding ->
            binding.attributes.map { a ->
                GlAttribPointer(
                    b, a.location, a.type.components, if (a.type.isInteger) GL_INT else GL_FLOAT, a.type.isInteger,
                    binding.stride, a.offset.toLong(), if (binding.perInstance) 1 else 0,
                )
            }
        }
    }
}

/** A VAO: every layout attribute enabled and pointed at its buffer, plus the index buffer. */
internal class GlVertexInput(
    override val layout: GlVertexLayout,
    vertexBuffers: List<RhiBuffer>,
    indexBuffer: RhiBuffer?,
) : RhiVertexInput {
    val vao: Int = glGenVertexArrays()
    private var closed = false

    init {
        require(vertexBuffers.size == layout.desc.bindings.size) {
            "Layout has ${layout.desc.bindings.size} bindings but got ${vertexBuffers.size} vertex buffers"
        }
        glBindVertexArray(vao)
        var bound = -1
        for (p in layout.pointers) {
            if (p.binding != bound) {
                glBindBuffer(GL_ARRAY_BUFFER, (vertexBuffers[p.binding] as GlBuffer).id)
                bound = p.binding
            }
            if (p.integer) glVertexAttribIPointer(p.location, p.components, p.glType, p.stride, p.offset)
            else glVertexAttribPointer(p.location, p.components, p.glType, false, p.stride, p.offset)
            glEnableVertexAttribArray(p.location)
            if (p.divisor != 0) glVertexAttribDivisor(p.location, p.divisor)
        }
        indexBuffer?.let { glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, (it as GlBuffer).id) }
        glBindVertexArray(0)
        // VAO binds above bypassed the binding cache
        GLBindings.cache.invalidate()
    }

    override fun close() {
        if (closed) return
        closed = true
        glDeleteVertexArrays(vao)
    }
}
