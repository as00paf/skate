package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glBufferSubData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL30.glBindBufferBase
import org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER

/** Uniform buffer with up to [LightPacking.MAX_LIGHTS] lights (`LightBlock`), plus the per-object index list. GL thread only. */
class LightBuffer {
    private var buffer = 0
    private val staging = BufferUtils.createFloatBuffer(LightPacking.MAX_LIGHTS * LightPacking.FLOATS_PER_LIGHT)

    var lightCount = 0
        private set

    fun upload(lights: List<LightData>) {
        if (buffer == 0) {
            buffer = glGenBuffers()
            glBindBuffer(GL_UNIFORM_BUFFER, buffer)
            glBufferData(GL_UNIFORM_BUFFER, LightPacking.MAX_LIGHTS.toLong() * LightPacking.BYTES_PER_LIGHT, GL_DYNAMIC_DRAW)
        }
        staging.clear()
        lightCount = LightPacking.pack(lights, staging)
        staging.flip()
        glBindBuffer(GL_UNIFORM_BUFFER, buffer)
        glBufferSubData(GL_UNIFORM_BUFFER, 0L, staging)
    }

    fun bind(shader: Shader) {
        if (buffer == 0) return
        shader.bindUniformBlock(BLOCK_NAME, BINDING)
        glBindBufferBase(GL_UNIFORM_BUFFER, BINDING, buffer)
        shader.uploadInt(Uniforms.LIGHT_COUNT, lightCount)
    }

    /** Selects which lights shade the next draw; [indices] holds [count] entries into the uploaded list. */
    fun setObjectLights(shader: Shader, indices: IntArray, count: Int) {
        shader.uploadInt(Uniforms.OBJECT_LIGHT_COUNT, count)
        shader.uploadIntArray(Uniforms.OBJECT_LIGHTS, indices)
    }

    fun destroy() {
        if (buffer != 0) glDeleteBuffers(buffer)
        buffer = 0
        lightCount = 0
    }

    companion object {
        const val BLOCK_NAME = "LightBlock"
        const val BINDING = 2
    }
}
