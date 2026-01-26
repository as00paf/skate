package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.models.RawModel
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.*
import java.nio.FloatBuffer
import java.nio.IntBuffer

class VAOLoader {

    private val vaos = mutableListOf<Int>()
    private val vbos = mutableListOf<Int>()
    private val textures = mutableListOf<Int>()

    fun loadToVAO(
        positions: FloatArray, 
        textureCoords: FloatArray, 
        normals: FloatArray, 
        indices: IntArray, 
        rawVertices: FloatArray = floatArrayOf(),
        tangents: FloatArray = floatArrayOf(),
        colors: FloatArray = floatArrayOf(),
        drawMode: Int = GL_TRIANGLES,
        textureCoords1: FloatArray = floatArrayOf(),
        joints: IntArray = intArrayOf(),
        weights: FloatArray = floatArrayOf()
    ): RawModel {
        val vaoId = createVAO()
        if (indices.isNotEmpty()) {
            bindIndicesBuffer(indices)
        }
        storeDataInAttribList(0, 3, positions)
        storeDataInAttribList(1, 2, textureCoords)
        storeDataInAttribList(2, 3, normals)
        
        if (tangents.isNotEmpty()) {
            storeDataInAttribList(3, 3, tangents)
        }
        if (colors.isNotEmpty()) {
            storeDataInAttribList(4, 4, colors)
        }
        if (textureCoords1.isNotEmpty()) {
            storeDataInAttribList(5, 2, textureCoords1)
        }
        if (joints.isNotEmpty()) {
            storeDataInAttribListInt(6, 4, joints)
        }
        if (weights.isNotEmpty()) {
            storeDataInAttribList(7, 4, weights)
        }
        
        unbindVAO()
        val vertexCount = if (indices.isNotEmpty()) indices.size else positions.size / 3
        return RawModel(vaoId, vertexCount, rawVertices, drawMode)
    }

    private fun storeDataInAttribListInt(attributeNumber: Int, coordinateSize: Int, data: IntArray) {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        val buffer = storeDataInIntBuffer(data)
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        glVertexAttribIPointer(attributeNumber, coordinateSize, GL_INT, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    fun loadToVAO(positions: FloatArray, coordinateSize: Int, rawVertices: FloatArray = floatArrayOf()): RawModel {
        val vaoId = createVAO()
        storeDataInAttribList(0, coordinateSize, positions)
        unbindVAO()
        return RawModel(vaoId, positions.size / coordinateSize, rawVertices)
    }

    private fun createVAO(): Int {
        val vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        vaos.add(vaoId)
        return vaoId
    }

    fun loadTexture(fileName: String):Int {
        val id = AssetPool.getTexture(fileName).getId()
        textures.add(id)

        return id
    }

    private fun storeDataInAttribList(attributeNumber: Int, coordinateSize: Int, data: FloatArray) {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        val buffer = storeDataInFloatBuffer(data)
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        glVertexAttribPointer(attributeNumber, coordinateSize, GL_FLOAT, false, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    private fun unbindVAO() {
        glBindVertexArray(0)
    }

    private fun bindIndicesBuffer(indices: IntArray) {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId)
        val buffer = storeDataInIntBuffer(indices)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
    }

    private fun storeDataInFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = BufferUtils.createFloatBuffer(data.size)
        buffer.put(data)
        buffer.flip()
        return buffer
    }

    private fun storeDataInIntBuffer(data: IntArray): IntBuffer {
        val buffer = BufferUtils.createIntBuffer(data.size)
        buffer.put(data)
        buffer.flip()
        return buffer
    }

    fun cleanUp() {
        vaos.forEach { glDeleteVertexArrays(it) }
        vbos.forEach { glDeleteBuffers(it) }
        textures.forEach { glDeleteTextures(it) }
    }

}