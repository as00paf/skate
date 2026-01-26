package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.models.RawModel
import org.joml.Vector2f
import org.joml.Vector3f
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.Exception

class ObjLoader {

    fun loadObjModel(fileName: String, loader: VAOLoader): RawModel {
        try {
            val fr = FileReader(File(fileName))
            val reader = BufferedReader(fr)
            var line: String?
            val vertices = mutableListOf<Vector3f>()
            val textures = mutableListOf<Vector2f>()
            val normals = mutableListOf<Vector3f>()
            val indices = mutableListOf<Int>()
            val normalsArray: FloatArray
            val texturesArray: FloatArray

            while(true) {
                line = reader.readLine()
                val currentLine = line.split(" ")
                if(line.startsWith("v ")) {
                    val vertex = Vector3f(currentLine[1].toFloat(), currentLine[2].toFloat(), currentLine[3].toFloat())
                    vertices.add(vertex)
                }else if(line.startsWith("vt ")) {
                    val texture = Vector2f(currentLine[1].toFloat(), currentLine[2].toFloat())
                    textures.add(texture)
                }else if(line.startsWith("vn ")) {
                    val normal = Vector3f(currentLine[1].toFloat(), currentLine[2].toFloat(), currentLine[3].toFloat())
                    normals.add(normal)
                }else if(line.startsWith("f ")) {
                    texturesArray = FloatArray(vertices.size * 2)
                    normalsArray = FloatArray(vertices.size * 3)
                    break
                }
            }

            while(line != null) {
                if(!line.startsWith("f ")) {
                    line = reader.readLine()
                    continue
                }

                val currentLine = line.split(" ")
                val vertex1 = currentLine[1].split("/")
                val vertex2 = currentLine[2].split("/")
                val vertex3 = currentLine[3].split("/")

                processVertex(vertex1, indices, textures, normals, texturesArray, normalsArray)
                processVertex(vertex2, indices, textures, normals, texturesArray, normalsArray)
                processVertex(vertex3, indices, textures, normals, texturesArray, normalsArray)
                line = reader.readLine()
            }

            reader.close()

            val verticesArray = FloatArray(vertices.size * 3)
            val indicesArray = IntArray(indices.size)

            var vertexPointer = 0
            vertices.forEach { vertex ->
                verticesArray[vertexPointer++] = vertex.x
                verticesArray[vertexPointer++] = vertex.y
                verticesArray[vertexPointer++] = vertex.z
            }

            for(i in 0 until indices.size) {
                indicesArray[i] = indices[i]
            }

            return loader.loadToVAO(verticesArray, texturesArray, normalsArray, indicesArray)
        } catch (e: Exception) {
            println("Could not load obj file $fileName")
            throw e
        }
    }

    private fun processVertex(vertexData: List<String>, indices: MutableList<Int>, textures: List<Vector2f>, normals: List<Vector3f>, textureArray: FloatArray, normalsArray: FloatArray) {
        val currentVertexPointer = vertexData[0].toInt() - 1
        indices.add(currentVertexPointer)
        val currentTex = textures[vertexData[1].toInt() - 1]
        textureArray[currentVertexPointer*2] = currentTex.x
        textureArray[currentVertexPointer*2 + 1] = 1 - currentTex.y
        val currentNorm = normals[vertexData[2].toInt() - 1]
        normalsArray[currentVertexPointer * 3] = currentNorm.x
        normalsArray[currentVertexPointer * 3 + 1] = currentNorm.y
        normalsArray[currentVertexPointer * 3 + 2] = currentNorm.z
    }

    companion object {
        const val CUBE = "assets/obj/cube.obj"
        const val SKATEBOARD_GLB = "assets/obj/skateboard_free_model.glb"
    }
}