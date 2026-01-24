package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.render.VAOLoader
import org.lwjgl.assimp.*
import org.lwjgl.assimp.Assimp.*
import java.nio.IntBuffer

class AssimpLoader {

    fun loadModel(filePath: String, loader: VAOLoader): RawModel {
        val scene = aiImportFile(filePath, aiProcess_Triangulate or aiProcess_FlipUVs or aiProcess_JoinIdenticalVertices)
            ?: throw RuntimeException("Error loading model: " + aiGetErrorString())

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        var vertexOffset = 0
        
        for (i in 0 until scene.mNumMeshes()) {
            val mesh = AIMesh.create(scene.mMeshes()!!.get(i))
            
            // Vertices & Normals
            for (v in 0 until mesh.mNumVertices()) {
                val vertex = mesh.mVertices().get(v)
                vertices.add(vertex.x())
                vertices.add(vertex.y())
                vertices.add(vertex.z())

                val normal = mesh.mNormals()!!.get(v)
                normals.add(normal.x())
                normals.add(normal.y())
                normals.add(normal.z())

                if (mesh.mTextureCoords(0) != null) {
                    val texCoord = mesh.mTextureCoords(0)!!.get(v)
                    texCoords.add(texCoord.x())
                    texCoords.add(texCoord.y())
                } else {
                    texCoords.add(0f)
                    texCoords.add(0f)
                }
            }

            // Indices
            for (f in 0 until mesh.mNumFaces()) {
                val face = mesh.mFaces().get(f)
                for (ind in 0 until face.mNumIndices()) {
                    indices.add(face.mIndices().get(ind) + vertexOffset)
                }
            }
            
            vertexOffset += mesh.mNumVertices()
        }

        aiReleaseImport(scene)

        return loader.loadToVAO(
            vertices.toFloatArray(),
            texCoords.toFloatArray(),
            normals.toFloatArray(),
            indices.toIntArray()
        )
    }
}