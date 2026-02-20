package com.pafoid.skate.engine.assets.data

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20.glDeleteProgram
import org.lwjgl.opengl.GL20.glDeleteShader
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform1iv
import org.lwjgl.opengl.GL20.glUniform2f
import org.lwjgl.opengl.GL20.glUniform3f
import org.lwjgl.opengl.GL20.glUniform4f
import org.lwjgl.opengl.GL20.glUniformMatrix4fv
import org.lwjgl.opengl.GL20.glUseProgram

class Shader(
    private val shaderProgId: Int = -1,
    private val vertexShaderId: Int = -1,
    private val fragmentShaderId: Int = -1
) {
    private var isUsed = false

    /**
     * Cache for uniform locations to avoid expensive glGetUniformLocation calls.
     * Uniform locations are stable for the lifetime of a shader program.
     */
    private val uniformCache = mutableMapOf<String, Int>()

    /**
     * Reusable buffers to minimize garbage collection pressure in hot paths.
     * These are cleared and reused on each call.
     */
    private val matrixBuffer = BufferUtils.createFloatBuffer(16)
    private val vec2Buffer = BufferUtils.createFloatBuffer(2)
    private val vec3Buffer = BufferUtils.createFloatBuffer(3)
    private val vec4Buffer = BufferUtils.createFloatBuffer(4)

    /**
     * Gets the location of a uniform variable, using cache to avoid repeated
     * expensive glGetUniformLocation calls.
     *
     * @param varName The name of the uniform variable
     * @return The location of the uniform, or -1 if not found
     */
    private fun getLocation(varName: String): Int {
        return uniformCache.getOrPut(varName) {
            glGetUniformLocation(shaderProgId, varName)
        }
    }

    fun start() {
        if (!isUsed) {
            glUseProgram(shaderProgId)
            isUsed = true
        }
    }

    fun stop() {
        glUseProgram(0)
        isUsed = false
    }

    fun uploadMat4f(varName: String, mat4f: Matrix4f) {
        val varLocation = getLocation(varName)
        start()
        matrixBuffer.clear()
        mat4f.get(matrixBuffer)
        glUniformMatrix4fv(varLocation, false, matrixBuffer)
    }

    fun uploadMat3f(varName: String, mat3f: Matrix3f) {
        val varLocation = getLocation(varName)
        start()
        matrixBuffer.clear()
        mat3f.get(matrixBuffer)
        glUniformMatrix4fv(varLocation, false, matrixBuffer)
    }

    fun uploadVec2f(varName: String, vec: Vector2f) {
        val varLocation = getLocation(varName)
        start()
        vec2Buffer.clear()
        vec2Buffer.put(vec.x).put(vec.y).flip()
        glUniform2f(varLocation, vec2Buffer.get(), vec2Buffer.get())
    }

    fun uploadVec3f(varName: String, vec: Vector3f) {
        val varLocation = getLocation(varName)
        start()
        vec3Buffer.clear()
        vec3Buffer.put(vec.x).put(vec.y).put(vec.z).flip()
        glUniform3f(varLocation, vec3Buffer.get(), vec3Buffer.get(), vec3Buffer.get())
    }

    fun uploadVec4f(varName: String, vec: Vector4f) {
        val varLocation = getLocation(varName)
        start()
        vec4Buffer.clear()
        vec4Buffer.put(vec.x).put(vec.y).put(vec.z).put(vec.w).flip()
        glUniform4f(varLocation, vec4Buffer.get(), vec4Buffer.get(), vec4Buffer.get(), vec4Buffer.get())
    }

    fun uploadFloat(varName: String, value: Float) {
        val varLocation = getLocation(varName)
        start()
        glUniform1f(varLocation, value)
    }

    fun uploadInt(varName: String, value: Int) {
        val varLocation = getLocation(varName)
        start()
        glUniform1i(varLocation, value)
    }

    fun uploadIntArray(varName: String, array: IntArray) {
        val varLocation = getLocation(varName)
        start()
        glUniform1iv(varLocation, array)
    }

    fun uploadTexture(varName: String, slot: Int) {
        val varLocation = getLocation(varName)
        start()
        glUniform1i(varLocation, slot)
    }

    fun uploadBoolean(varName: String, value: Boolean) {
        val varLocation = getLocation(varName)
        start()
        val boolValue = if (value) 1 else 0
        glUniform1i(varLocation, boolValue)
    }

    fun uploadMat4fArray(varName: String, matrices: Array<Matrix4f>) {
        val varLocation = getLocation(varName)
        start()
        val bufferSize = matrices.size * 16
        // For large arrays, allocate a new buffer to avoid resizing issues
        val buffer = if (bufferSize <= 16) {
            matrixBuffer.clear()
            matrixBuffer
        } else {
            BufferUtils.createFloatBuffer(bufferSize)
        }
        for (mat in matrices) {
            mat.get(buffer)
            buffer.position(buffer.position() + 16)
        }
        buffer.flip()
        glUniformMatrix4fv(varLocation, false, buffer)
    }

    fun destroy() {
        stop()
        glDeleteShader(vertexShaderId)
        glDeleteShader(fragmentShaderId)
        glDeleteProgram(shaderProgId)
        uniformCache.clear()
    }
}
