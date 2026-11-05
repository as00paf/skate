package com.pafoid.skate.engine.assets.data

import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiShader
import com.pafoid.skate.engine.render.rhi.ShaderParams
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * A shader asset: the device's [RhiShader] plus its named [ShaderParams] (REN-12 step 5). Uploads keep today's named
 * API; the backend skips values the program already holds. Main thread only.
 */
class Shader(rhi: RhiShader) {
    /** The device shader; null once [destroy]ed. */
    var rhi: RhiShader? = rhi
        private set
    private var params: ShaderParams? = rhi.parameters()

    /** Reused for matrix uploads (main thread only). */
    private val matrix = FloatArray(16)

    /** Links the std140 uniform block [blockName] to [binding]. Returns false if the shader lacks it. */
    fun bindUniformBlock(blockName: String, binding: Int): Boolean = rhi?.bindUniformBlock(blockName, binding) ?: false

    /** Makes this shader the one subsequent draws use. */
    fun start() {
        params?.let { RhiContext.require().commandList().setParams(it) }
    }

    /** End of this shader's draws. The program stays bound until another shader starts (no GL call). */
    fun stop() {}

    fun uploadMat4f(varName: String, mat4f: Matrix4f) {
        val p = started() ?: return
        mat4f.get(matrix)
        p.setMatrix4(varName, matrix)
    }

    fun uploadVec2f(varName: String, vec: Vector2f) {
        started()?.set(varName, vec.x, vec.y)
    }

    fun uploadVec3f(varName: String, vec: Vector3f) {
        started()?.set(varName, vec.x, vec.y, vec.z)
    }

    fun uploadVec4f(varName: String, vec: Vector4f) {
        started()?.set(varName, vec.x, vec.y, vec.z, vec.w)
    }

    fun uploadFloat(varName: String, value: Float) {
        started()?.set(varName, value)
    }

    fun uploadInt(varName: String, value: Int) {
        started()?.set(varName, value)
    }

    fun uploadIntArray(varName: String, array: IntArray) {
        started()?.set(varName, array)
    }

    fun uploadTexture(varName: String, slot: Int) {
        started()?.set(varName, slot)
    }

    fun uploadBoolean(varName: String, value: Boolean) {
        started()?.set(varName, if (value) 1 else 0)
    }

    /** Uploads also make the shader current, as they always did (callers draw right after). */
    private fun started(): ShaderParams? {
        start()
        return params
    }

    /**
     * Hot reload: frees this shader's program and adopts [fresh]'s, so every holder of this Shader sees the new
     * program. [fresh] is left empty and must not be used afterwards.
     */
    fun replaceWith(fresh: Shader) {
        destroy()
        rhi = fresh.rhi
        params = fresh.params
        fresh.rhi = null
        fresh.params = null
    }

    fun destroy() {
        rhi?.close()
        rhi = null
        params = null
    }
}
