package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.render.VAOLoader
import org.lwjgl.assimp.*
import org.lwjgl.assimp.Assimp.*
import java.io.File
import java.nio.IntBuffer

class AssimpLoader {

    data class LoadedMeshPart(
        val model: RawModel,
        val texture: Texture
    )

    data class PreLoadedMeshPart(
        val vertices: FloatArray,
        val texCoords: FloatArray,
        val normals: FloatArray,
        val indices: IntArray,
        val texturePath: String?,
        val embeddedBuffer: java.nio.ByteBuffer?
    )

    data class PreLoadedModel(
        val parts: List<PreLoadedMeshPart>
    )

    fun preLoadModel(filePath: String): PreLoadedModel {
        val scene = aiImportFile(filePath, aiProcess_Triangulate or aiProcess_FlipUVs or aiProcess_JoinIdenticalVertices or aiProcess_PreTransformVertices)
            ?: throw RuntimeException("Error loading model: " + aiGetErrorString())

        val meshParts = mutableListOf<PreLoadedMeshPart>()

        for (i in 0 until scene.mNumMeshes()) {
            val mesh = AIMesh.create(scene.mMeshes()!!.get(i))
            
            var texturePath: String? = null
            var embeddedBuffer: java.nio.ByteBuffer? = null
            
            val materialIndex = mesh.mMaterialIndex()
            if (materialIndex >= 0) {
                val material = AIMaterial.create(scene.mMaterials()!!.get(materialIndex))
                val path = AIString.calloc()
                
                val types = intArrayOf(12, aiTextureType_DIFFUSE, aiTextureType_AMBIENT, aiTextureType_UNKNOWN, aiTextureType_EMISSIVE)
                for (type in types) {
                    val result = aiGetMaterialTexture(material, type, 0, path, null as IntBuffer?, null, null, null, null, null)
                    if (result == aiReturn_SUCCESS) {
                        val p = path.dataString()
                        if (p.isNotEmpty()) {
                            if (p.startsWith("*")) {
                                val index = p.substring(1).toInt()
                                val tex = AITexture.create(scene.mTextures()!!.get(index))
                                if (tex.mHeight() == 0) {
                                    // Extract buffer immediately while scene is open
                                    val originalBuffer = org.lwjgl.system.MemoryUtil.memByteBuffer(tex.pcData().address(), tex.mWidth())
                                    // Copy buffer to ensure it lives past aiReleaseImport
                                    embeddedBuffer = java.nio.ByteBuffer.allocateDirect(originalBuffer.remaining())
                                    embeddedBuffer.put(originalBuffer)
                                    embeddedBuffer.flip()
                                    texturePath = "Embedded::$filePath::$index"
                                }
                            } else {
                                texturePath = p
                            }
                            break 
                        }
                    }
                }
                path.free()
            }

            val vertices = FloatArray(mesh.mNumVertices() * 3)
            val normals = FloatArray(mesh.mNumVertices() * 3)
            val texCoords = FloatArray(mesh.mNumVertices() * 2)
            
            for (v in 0 until mesh.mNumVertices()) {
                val vertex = mesh.mVertices().get(v)
                vertices[v * 3] = vertex.x()
                vertices[v * 3 + 1] = vertex.y()
                vertices[v * 3 + 2] = vertex.z()

                val normal = mesh.mNormals()!!.get(v)
                normals[v * 3] = normal.x()
                normals[v * 3 + 1] = normal.y()
                normals[v * 3 + 2] = normal.z()

                if (mesh.mTextureCoords(0) != null) {
                    val texCoord = mesh.mTextureCoords(0)!!.get(v)
                    texCoords[v * 2] = texCoord.x()
                    texCoords[v * 2 + 1] = texCoord.y()
                } else {
                    texCoords[v * 2] = 0f
                    texCoords[v * 2 + 1] = 0f
                }
            }

            val indices = IntArray(mesh.mNumFaces() * 3)
            var indexPtr = 0
            for (f in 0 until mesh.mNumFaces()) {
                val face = mesh.mFaces().get(f)
                for (ind in 0 until face.mNumIndices()) {
                    indices[indexPtr++] = face.mIndices().get(ind)
                }
            }

            meshParts.add(PreLoadedMeshPart(vertices, texCoords, normals, indices, texturePath, embeddedBuffer))
        }

        aiReleaseImport(scene)
        return PreLoadedModel(meshParts)
    }

    fun loadModel(filePath: String, loader: VAOLoader): List<LoadedMeshPart> {
        val preLoaded = preLoadModel(filePath)
        return preLoaded.parts.map { p ->
            val model = loader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices)
            val texture = if (p.texturePath != null) {
                if (p.embeddedBuffer != null) {
                    AssetPool.getTexture(p.texturePath, p.embeddedBuffer)
                } else {
                    val finalPath = File(filePath).parentFile.resolve(p.texturePath).path
                    AssetPool.getTexture(finalPath)
                }
            } else {
                AssetPool.getTexture(Texture.WHITE)
            }
            LoadedMeshPart(model, texture)
        }
    }
}