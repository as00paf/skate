package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.models.RawModel
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.*
import java.nio.FloatBuffer
import java.nio.IntBuffer

class VAOLoader {

    private val vaos = mutableListOf<Int>()
    private val vbos = mutableListOf<Int>()
    private val vaoVboMap = mutableMapOf<Int, MutableList<Int>>()

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
        val currentVbos = mutableListOf<Int>()
        val enabledAttribs = mutableListOf<Int>()
        
        if (indices.isNotEmpty()) {
            currentVbos.add(bindIndicesBuffer(indices))
        }
        currentVbos.add(storeDataInAttribList(0, 3, positions))
        enabledAttribs.add(0)
        
        currentVbos.add(storeDataInAttribList(1, 2, textureCoords))
        enabledAttribs.add(1)
        
        currentVbos.add(storeDataInAttribList(2, 3, normals))
        enabledAttribs.add(2)
        
        if (tangents.isNotEmpty()) {
            currentVbos.add(storeDataInAttribList(3, 3, tangents))
            enabledAttribs.add(3)
        }
        if (colors.isNotEmpty()) {
            currentVbos.add(storeDataInAttribList(4, 4, colors))
            enabledAttribs.add(4)
        }
        if (textureCoords1.isNotEmpty()) {
            currentVbos.add(storeDataInAttribList(5, 2, textureCoords1))
            enabledAttribs.add(5)
        }
        if (joints.isNotEmpty()) {
            currentVbos.add(storeDataInAttribListInt(6, 4, joints))
            enabledAttribs.add(6)
        }
        if (weights.isNotEmpty()) {
            currentVbos.add(storeDataInAttribList(7, 4, weights))
            enabledAttribs.add(7)
        }
        
        unbindVAO()
        
        vaoVboMap[vaoId] = currentVbos

        val vertexCount = if (indices.isNotEmpty()) indices.size else positions.size / 3
        return RawModel(vaoId, vertexCount, rawVertices, drawMode, enabledAttribs)
    }

    private fun storeDataInAttribListInt(attributeNumber: Int, coordinateSize: Int, data: IntArray): Int {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        val buffer = storeDataInIntBuffer(data)
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        glVertexAttribIPointer(attributeNumber, coordinateSize, GL_INT, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        return vboId
    }

    fun loadToVAO(positions: FloatArray, coordinateSize: Int, rawVertices: FloatArray = floatArrayOf()): RawModel {
        val vaoId = createVAO()
        val vboId = storeDataInAttribList(0, coordinateSize, positions)
        unbindVAO()
        
        vaoVboMap[vaoId] = mutableListOf(vboId)
        
        return RawModel(vaoId, positions.size / coordinateSize, rawVertices, enabledAttributes = listOf(0))
    }

    private fun createVAO(): Int {
        val vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)
        vaos.add(vaoId)
        return vaoId
    }

    private fun storeDataInAttribList(attributeNumber: Int, coordinateSize: Int, data: FloatArray): Int {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        val buffer = storeDataInFloatBuffer(data)
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        glVertexAttribPointer(attributeNumber, coordinateSize, GL_FLOAT, false, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        return vboId
    }

    private fun unbindVAO() {
        glBindVertexArray(0)
    }

    private fun bindIndicesBuffer(indices: IntArray): Int {
        val vboId = glGenBuffers()
        vbos.add(vboId)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId)
        val buffer = storeDataInIntBuffer(indices)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        return vboId
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
        vaos.clear()
        vbos.clear()
        vaoVboMap.clear()
    }

    fun deleteVAO(vaoId: Int) {
        if (vaos.contains(vaoId)) {
            glDeleteVertexArrays(vaoId)
            vaos.remove(vaoId)
            
            vaoVboMap[vaoId]?.let { associatedVbos ->
                associatedVbos.forEach { vboId ->
                    glDeleteBuffers(vboId)
                    vbos.remove(vboId)
                }
                vaoVboMap.remove(vaoId)
            }
        }
    }

}