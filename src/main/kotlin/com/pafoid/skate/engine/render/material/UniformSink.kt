package com.pafoid.skate.engine.render.material

import com.pafoid.skate.engine.assets.data.Shader
import org.joml.Vector3f
import org.joml.Vector4f

/** Destination for material uniform uploads; keeps [MaterialBinder] GL-free. */
interface UniformSink {
    fun setFloat(name: String, value: Float)
    fun setInt(name: String, value: Int)
    fun setBool(name: String, value: Boolean)
    fun setVec2(name: String, x: Float, y: Float)
    fun setVec3(name: String, x: Float, y: Float, z: Float)
    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float)
}

class ShaderUniformSink(private val shader: Shader) : UniformSink {
    private val v3 = Vector3f()
    private val v4 = Vector4f()
    private val v2 = org.joml.Vector2f()

    override fun setFloat(name: String, value: Float) = shader.uploadFloat(name, value)
    override fun setInt(name: String, value: Int) = shader.uploadInt(name, value)
    override fun setBool(name: String, value: Boolean) = shader.uploadBoolean(name, value)
    override fun setVec2(name: String, x: Float, y: Float) = shader.uploadVec2f(name, v2.set(x, y))
    override fun setVec3(name: String, x: Float, y: Float, z: Float) = shader.uploadVec3f(name, v3.set(x, y, z))
    override fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) = shader.uploadVec4f(name, v4.set(x, y, z, w))
}
