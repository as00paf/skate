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
        val material: com.pafoid.skate.engine.models.Material
    )

    data class PreLoadedMeshPart(
        val vertices: FloatArray,
        val texCoords: FloatArray,
        val texCoords1: FloatArray,
        val normals: FloatArray,
        val tangents: FloatArray,
        val colors: FloatArray,
        val joints: IntArray,
        val weights: FloatArray,
        val indices: IntArray,
        val material: com.pafoid.skate.engine.models.Material,
        val drawMode: Int,
        val embeddedTextures: Map<String, java.nio.ByteBuffer>
    )

    data class PreLoadedModel(
        val parts: List<PreLoadedMeshPart>
    )

    fun preLoadModel(filePath: String): PreLoadedModel {
        val scene = aiImportFile(filePath, aiProcess_Triangulate or aiProcess_FlipUVs or aiProcess_JoinIdenticalVertices or aiProcess_CalcTangentSpace)
            ?: throw RuntimeException("Error loading model: " + aiGetErrorString())

        val meshParts = mutableListOf<PreLoadedMeshPart>()
        val embeddedTextures = mutableMapOf<String, java.nio.ByteBuffer>()

        processNode(scene.mRootNode()!!, scene, org.joml.Matrix4f(), meshParts, embeddedTextures, filePath)

        aiReleaseImport(scene)
        return PreLoadedModel(meshParts)
    }

    private fun processNode(node: AINode, scene: AIScene, parentTransform: org.joml.Matrix4f, meshParts: MutableList<PreLoadedMeshPart>, embeddedTextures: MutableMap<String, java.nio.ByteBuffer>, filePath: String) {
        val nodeTransform = parentTransform.mul(toJomlMatrix(node.mTransformation()), org.joml.Matrix4f())

        for (i in 0 until node.mNumMeshes()) {
            val meshIndex = node.mMeshes()!!.get(i)
            val mesh = AIMesh.create(scene.mMeshes()!!.get(meshIndex))
            meshParts.add(processMesh(mesh, scene, nodeTransform, embeddedTextures, filePath))
        }

        for (i in 0 until node.mNumChildren()) {
            val child = AINode.create(node.mChildren()!!.get(i))
            processNode(child, scene, nodeTransform, meshParts, embeddedTextures, filePath)
        }
    }

    private fun processMesh(mesh: AIMesh, scene: AIScene, transform: org.joml.Matrix4f, embeddedTextures: MutableMap<String, java.nio.ByteBuffer>, filePath: String): PreLoadedMeshPart {
        val materialData = com.pafoid.skate.engine.models.Material()
        
        val materialIndex = mesh.mMaterialIndex()
        if (materialIndex >= 0) {
            val material = AIMaterial.create(scene.mMaterials()!!.get(materialIndex))
            
            materialData.baseColorTexture = loadMaterialTexture(scene, material, aiTextureType_DIFFUSE, filePath, embeddedTextures) ?: 
                                           loadMaterialTexture(scene, material, aiTextureType_BASE_COLOR, filePath, embeddedTextures)
            
            materialData.normalMap = loadMaterialTexture(scene, material, aiTextureType_NORMALS, filePath, embeddedTextures)
            
            materialData.metallicRoughnessTexture = loadMaterialTexture(scene, material, aiTextureType_METALNESS, filePath, embeddedTextures) ?:
                                                   loadMaterialTexture(scene, material, aiTextureType_UNKNOWN, filePath, embeddedTextures)
            
            materialData.aoTexture = loadMaterialTexture(scene, material, aiTextureType_AMBIENT_OCCLUSION, filePath, embeddedTextures) ?:
                                    loadMaterialTexture(scene, material, aiTextureType_LIGHTMAP, filePath, embeddedTextures)
            
            materialData.emissiveTexture = loadMaterialTexture(scene, material, aiTextureType_EMISSIVE, filePath, embeddedTextures)
            
            val color = AIColor4D.create()
            if (aiGetMaterialColor(material, AI_MATKEY_COLOR_DIFFUSE, 0, 0, color) == aiReturn_SUCCESS) {
                materialData.baseColorFactor.set(color.r(), color.g(), color.b(), color.a())
            }

            val doubleSided = IntArray(1)
            if (aiGetMaterialIntegerArray(material, AI_MATKEY_TWOSIDED, 0, 0, doubleSided, intArrayOf(1)) == aiReturn_SUCCESS) {
                materialData.doubleSided = doubleSided[0] != 0
            }

            val alphaModeString = AIString.calloc()
            if (aiGetMaterialString(material, AI_MATKEY_GLTF_ALPHAMODE, 0, 0, alphaModeString) == aiReturn_SUCCESS) {
                materialData.alphaMode = alphaModeString.dataString()
            }
            alphaModeString.free()

            val alphaCutoffArray = FloatArray(1)
            if (aiGetMaterialFloatArray(material, AI_MATKEY_GLTF_ALPHACUTOFF, 0, 0, alphaCutoffArray, intArrayOf(1)) == aiReturn_SUCCESS) {
                materialData.alphaCutoff = alphaCutoffArray[0]
            }
        }

        val numVertices = mesh.mNumVertices()
        val vertices = FloatArray(numVertices * 3)
        val normals = FloatArray(numVertices * 3)
        val texCoords = FloatArray(numVertices * 2)
        val texCoords1 = FloatArray(numVertices * 2)
        val tangents = FloatArray(numVertices * 3)
        val colors = FloatArray(numVertices * 4)
        val joints = IntArray(numVertices * 4)
        val weights = FloatArray(numVertices * 4)
        
        for (v in 0 until numVertices) {
            val vertex = mesh.mVertices().get(v)
            val vVec = org.joml.Vector3f(vertex.x(), vertex.y(), vertex.z())
            transform.transformPosition(vVec)
            vertices[v * 3] = vVec.x
            vertices[v * 3 + 1] = vVec.y
            vertices[v * 3 + 2] = vVec.z

            val normal = mesh.mNormals()!!.get(v)
            val nVec = org.joml.Vector3f(normal.x(), normal.y(), normal.z())
            transform.transformDirection(nVec)
            normals[v * 3] = nVec.x
            normals[v * 3 + 1] = nVec.y
            normals[v * 3 + 2] = nVec.z

            if (mesh.mTangents() != null) {
                val tangent = mesh.mTangents()!!.get(v)
                val tVec = org.joml.Vector3f(tangent.x(), tangent.y(), tangent.z())
                transform.transformDirection(tVec)
                tangents[v * 3] = tVec.x
                tangents[v * 3 + 1] = tVec.y
                tangents[v * 3 + 2] = tVec.z
            }

            if (mesh.mColors(0) != null) {
                val color = mesh.mColors(0)!!.get(v)
                colors[v * 4] = color.r()
                colors[v * 4 + 1] = color.g()
                colors[v * 4 + 2] = color.b()
                colors[v * 4 + 3] = color.a()
            } else {
                colors[v * 4] = 1f; colors[v * 4 + 1] = 1f; colors[v * 4 + 2] = 1f; colors[v * 4 + 3] = 1f
            }

            if (mesh.mTextureCoords(0) != null) {
                val texCoord = mesh.mTextureCoords(0)!!.get(v)
                texCoords[v * 2] = texCoord.x()
                texCoords[v * 2 + 1] = texCoord.y()
            }

            if (mesh.mTextureCoords(1) != null) {
                val texCoord = mesh.mTextureCoords(1)!!.get(v)
                texCoords1[v * 2] = texCoord.x()
                texCoords1[v * 2 + 1] = texCoord.y()
            }
        }

        // Process Bones for Joints/Weights
        for (b in 0 until mesh.mNumBones()) {
            val bone = AIBone.create(mesh.mBones()!!.get(b))
            for (w in 0 until bone.mNumWeights()) {
                val weight = bone.mWeights().get(w)
                val vertexId = weight.mVertexId()
                val weightValue = weight.mWeight()
                
                // Find empty slot in joints/weights for this vertex
                for (slot in 0 until 4) {
                    if (weights[vertexId * 4 + slot] == 0f) {
                        joints[vertexId * 4 + slot] = b
                        weights[vertexId * 4 + slot] = weightValue
                        break
                    }
                }
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

        return PreLoadedMeshPart(vertices, texCoords, texCoords1, normals, tangents, colors, joints, weights, indices, materialData, org.lwjgl.opengl.GL11.GL_TRIANGLES, embeddedTextures)
    }

    private fun toJomlMatrix(aiMatrix: AIMatrix4x4): org.joml.Matrix4f {
        return org.joml.Matrix4f(
            aiMatrix.a1(), aiMatrix.b1(), aiMatrix.c1(), aiMatrix.d1(),
            aiMatrix.a2(), aiMatrix.b2(), aiMatrix.c2(), aiMatrix.d2(),
            aiMatrix.a3(), aiMatrix.b3(), aiMatrix.c3(), aiMatrix.d3(),
            aiMatrix.a4(), aiMatrix.b4(), aiMatrix.c4(), aiMatrix.d4()
        )
    }

    private fun loadMaterialTexture(scene: AIScene, material: AIMaterial, type: Int, modelPath: String, embeddedTextures: MutableMap<String, java.nio.ByteBuffer>): Texture? {
        val path = AIString.calloc()
        val result = aiGetMaterialTexture(material, type, 0, path, null as IntBuffer?, null, null, null, null, null)
        if (result != aiReturn_SUCCESS) {
            path.free()
            return null
        }
        
        val p = path.dataString()
        path.free()
        if (p.isEmpty()) return null
        
        return if (p.startsWith("*")) {
            val index = p.substring(1).toInt()
            val tex = AITexture.create(scene.mTextures()!!.get(index))
            val texturePath = "Embedded::$modelPath::$index"
            
            val originalBuffer = org.lwjgl.system.MemoryUtil.memByteBuffer(tex.pcData().address(), tex.mWidth())
            val copy = java.nio.ByteBuffer.allocateDirect(originalBuffer.remaining())
            copy.put(originalBuffer)
            copy.flip()
            embeddedTextures[texturePath] = copy
            AssetPool.getTexture(texturePath, copy)
        } else {
            val finalPath = File(modelPath).parentFile.resolve(p).path
            AssetPool.getTexture(finalPath)
        }
    }

    fun loadModel(filePath: String, loader: VAOLoader): List<LoadedMeshPart> {
        val preLoaded = preLoadModel(filePath)
        return preLoaded.parts.map { p ->
            val model = loader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1)
            LoadedMeshPart(model, p.material)
        }
    }
}