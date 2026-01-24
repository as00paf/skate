package com.pafoid.skate.engine.assets

import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20.*

class Shader(
    private val shaderProgId: Int = -1,
    private val vertexShaderId: Int = -1,
    private val fragmentShaderId: Int = -1
) {
    private var isUsed = false

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
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        val matBuffer = BufferUtils.createFloatBuffer(16)
        mat4f.get(matBuffer)
        glUniformMatrix4fv(varLocation, false, matBuffer)
    }

    fun uploadMat3f(varName: String, mat3f: Matrix3f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        val matBuffer = BufferUtils.createFloatBuffer(9)
        mat3f.get(matBuffer)
        glUniformMatrix4fv(varLocation, false, matBuffer)
    }

    fun uploadVec2f(varName: String, vec: Vector2f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        glUniform2f(varLocation, vec.x, vec.y)
    }

    fun uploadVec3f(varName: String, vec: Vector3f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        glUniform3f(varLocation, vec.x, vec.y, vec.z)
    }

    fun uploadVec4f(varName: String, vec: Vector4f) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        glUniform4f(varLocation, vec.x, vec.y, vec.z, vec.w)
    }

    fun uploadFloat(varName: String, value: Float) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        glUniform1f(varLocation, value)
    }

    fun uploadInt(varName: String, value: Int) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        glUniform1i(varLocation, value)
    }

    fun uploadIntArray(varName: String, array: IntArray) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        glUniform1iv(varLocation, array)
    }

    fun uploadTexture(varName: String, slot: Int) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        glUniform1i(varLocation, slot)
    }

    fun uploadBoolean(varName: String, value: Boolean) {
        val varLocation = glGetUniformLocation(shaderProgId, varName)
        start()
        val boolValue = if (value) 1 else 0
        glUniform1i(varLocation, boolValue)
    }

    fun destroy() {
        stop()
        glDeleteShader(vertexShaderId)
        glDeleteShader(fragmentShaderId)
        glDeleteProgram(shaderProgId)
    }

    companion object {
        const val SHADER_3D_DEFAULT = "assets/shaders/shader_3d_default.glsl"
        const val SHADER_2D_BATCH = "assets/shaders/shader_2d_batch.glsl"
        const val PICKING = "assets/shaders/picking.glsl"
        const val DEBUG = "assets/shaders/debugLine2D.glsl"
    }
}