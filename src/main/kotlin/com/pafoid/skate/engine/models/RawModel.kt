package com.pafoid.skate.engine.models

import org.lwjgl.opengl.GL11.GL_TRIANGLES

data class RawModel(
    val vaoId: Int, 
    val vertexCount: Int, 
    val vertices: FloatArray = floatArrayOf(),
    val drawMode: Int = GL_TRIANGLES
)