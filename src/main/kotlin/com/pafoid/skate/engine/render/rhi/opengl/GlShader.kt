package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.RhiShader
import com.pafoid.skate.engine.render.rhi.ShaderCompileError
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import com.pafoid.skate.engine.render.rhi.ShaderParams
import com.pafoid.skate.engine.render.rhi.ShaderStage
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20.GL_COMPILE_STATUS
import org.lwjgl.opengl.GL20.GL_FALSE
import org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER
import org.lwjgl.opengl.GL20.GL_INFO_LOG_LENGTH
import org.lwjgl.opengl.GL20.GL_LINK_STATUS
import org.lwjgl.opengl.GL20.GL_VERTEX_SHADER
import org.lwjgl.opengl.GL20.glAttachShader
import org.lwjgl.opengl.GL20.glCompileShader
import org.lwjgl.opengl.GL20.glCreateProgram
import org.lwjgl.opengl.GL20.glCreateShader
import org.lwjgl.opengl.GL20.glDeleteProgram
import org.lwjgl.opengl.GL20.glDeleteShader
import org.lwjgl.opengl.GL20.glGetProgramInfoLog
import org.lwjgl.opengl.GL20.glGetProgrami
import org.lwjgl.opengl.GL20.glGetShaderInfoLog
import org.lwjgl.opengl.GL20.glGetShaderi
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glLinkProgram
import org.lwjgl.opengl.GL20.glShaderSource
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform1iv
import org.lwjgl.opengl.GL20.glUniform2f
import org.lwjgl.opengl.GL20.glUniform3f
import org.lwjgl.opengl.GL20.glUniform4f
import org.lwjgl.opengl.GL20.glUniformMatrix4fv
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL31.GL_INVALID_INDEX
import org.lwjgl.opengl.GL31.glGetUniformBlockIndex
import org.lwjgl.opengl.GL31.glUniformBlockBinding

/** A linked GL program. Throws [ShaderCompileError] (after deleting what it created) when a stage fails. */
internal class GlShader(override val desc: ShaderDesc) : RhiShader {
    private val vertex = compile(GL_VERTEX_SHADER, desc.vertexSource, ShaderStage.VERTEX)
    private val fragment = compile(GL_FRAGMENT_SHADER, desc.fragmentSource, ShaderStage.FRAGMENT) { glDeleteShader(vertex) }
    val program: Int = link()
    private val params = GlShaderParams(program)
    private val linkedBlocks = HashSet<String>()
    private var closed = false

    override fun parameters(): ShaderParams = params

    override fun bindUniformBlock(blockName: String, binding: Int): Boolean {
        if (blockName in linkedBlocks) return true
        val index = glGetUniformBlockIndex(program, blockName)
        if (index == GL_INVALID_INDEX) return false
        glUniformBlockBinding(program, index, binding)
        linkedBlocks.add(blockName)
        return true
    }

    private fun compile(type: Int, source: String, stage: ShaderStage, cleanup: () -> Unit = {}): Int {
        val id = glCreateShader(type)
        glShaderSource(id, source)
        glCompileShader(id)
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            val log = glGetShaderInfoLog(id, glGetShaderi(id, GL_INFO_LOG_LENGTH))
            glDeleteShader(id)
            cleanup()
            throw ShaderCompileError(stage, log)
        }
        return id
    }

    private fun link(): Int {
        val id = glCreateProgram()
        glAttachShader(id, vertex)
        glAttachShader(id, fragment)
        glLinkProgram(id)
        if (glGetProgrami(id, GL_LINK_STATUS) == GL_FALSE) {
            val log = glGetProgramInfoLog(id, glGetProgrami(id, GL_INFO_LOG_LENGTH))
            glDeleteProgram(id)
            glDeleteShader(vertex)
            glDeleteShader(fragment)
            throw ShaderCompileError(ShaderStage.LINK, log)
        }
        return id
    }

    override fun close() {
        if (closed) return
        closed = true
        GlPrograms.forget(program)
        glDeleteShader(vertex)
        glDeleteShader(fragment)
        glDeleteProgram(program)
    }
}

/**
 * The context's current program. Every bind goes through [use] so a uniform upload knows whether its program is
 * current; [invalidate] after code outside the RHI (ImGui) may have bound another one.
 */
internal object GlPrograms {
    private const val UNKNOWN = -1
    private var current = UNKNOWN

    fun use(program: Int) {
        if (program == current) return
        glUseProgram(program)
        current = program
    }

    fun invalidate() {
        current = UNKNOWN
    }

    /** A deleted program stops being current (GL unbinds it on deletion only when it is not in use). */
    fun forget(program: Int) {
        if (program == current) current = UNKNOWN
    }
}

/** Uniform uploads of one program, skipping values it already holds ([UniformCache]). */
internal class GlShaderParams(val program: Int) : ShaderParams {
    private val cache = UniformCache { glGetUniformLocation(program, it) }
    private val matrix = BufferUtils.createFloatBuffer(16)

    override fun set(name: String, value: Float) {
        val slot = cache.slot(name)
        if (!cache.changed(slot, 1, value)) return
        GlPrograms.use(program)
        glUniform1f(slot.location, value)
    }

    override fun set(name: String, value: Int) {
        val slot = cache.slot(name)
        if (!cache.changed(slot, value)) return
        GlPrograms.use(program)
        glUniform1i(slot.location, value)
    }

    override fun set(name: String, x: Float, y: Float) {
        val slot = cache.slot(name)
        if (!cache.changed(slot, 2, x, y)) return
        GlPrograms.use(program)
        glUniform2f(slot.location, x, y)
    }

    override fun set(name: String, x: Float, y: Float, z: Float) {
        val slot = cache.slot(name)
        if (!cache.changed(slot, 3, x, y, z)) return
        GlPrograms.use(program)
        glUniform3f(slot.location, x, y, z)
    }

    override fun set(name: String, x: Float, y: Float, z: Float, w: Float) {
        val slot = cache.slot(name)
        if (!cache.changed(slot, 4, x, y, z, w)) return
        GlPrograms.use(program)
        glUniform4f(slot.location, x, y, z, w)
    }

    override fun set(name: String, values: IntArray) {
        val slot = cache.slot(name)
        if (!cache.changed(slot, values)) return
        GlPrograms.use(program)
        glUniform1iv(slot.location, values)
    }

    override fun setMatrix4(name: String, values: FloatArray) {
        val slot = cache.slot(name)
        if (!cache.changed(slot, values)) return
        GlPrograms.use(program)
        if (values.size == 16) {
            matrix.clear()
            matrix.put(values).flip()
            glUniformMatrix4fv(slot.location, false, matrix)
        } else {
            glUniformMatrix4fv(slot.location, false, values)
        }
    }
}
