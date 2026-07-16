package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.BoneNameMapper
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.BoneInfo
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.render.VAOLoader
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.assimp.AIBone
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.AITexture
import org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE
import org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHACUTOFF
import org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHAMODE
import org.lwjgl.assimp.Assimp.AI_MATKEY_TWOSIDED
import org.lwjgl.assimp.Assimp.aiGetErrorString
import org.lwjgl.assimp.Assimp.aiGetMaterialColor
import org.lwjgl.assimp.Assimp.aiGetMaterialFloatArray
import org.lwjgl.assimp.Assimp.aiGetMaterialIntegerArray
import org.lwjgl.assimp.Assimp.aiGetMaterialString
import org.lwjgl.assimp.Assimp.aiGetMaterialTexture
import org.lwjgl.assimp.Assimp.aiImportFile
import org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace
import org.lwjgl.assimp.Assimp.aiProcess_FlipUVs
import org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices
import org.lwjgl.assimp.Assimp.aiProcess_LimitBoneWeights
import org.lwjgl.assimp.Assimp.aiProcess_Triangulate
import org.lwjgl.assimp.Assimp.aiReleaseImport
import org.lwjgl.assimp.Assimp.aiReturn_SUCCESS
import org.lwjgl.assimp.Assimp.aiTextureType_AMBIENT_OCCLUSION
import org.lwjgl.assimp.Assimp.aiTextureType_BASE_COLOR
import org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE
import org.lwjgl.assimp.Assimp.aiTextureType_EMISSIVE
import org.lwjgl.assimp.Assimp.aiTextureType_LIGHTMAP
import org.lwjgl.assimp.Assimp.aiTextureType_METALNESS
import org.lwjgl.assimp.Assimp.aiTextureType_NORMALS
import org.lwjgl.assimp.Assimp.aiTextureType_UNKNOWN
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer

class AssimpLoader(
    private val textureLoader: TextureLoader,
    private val vaoLoader: VAOLoader,
) {

    fun loadModel(filePath: String): TexturedModel {
        val scene = aiImportFile(filePath, aiProcess_Triangulate or aiProcess_FlipUVs or aiProcess_JoinIdenticalVertices or aiProcess_CalcTangentSpace or aiProcess_LimitBoneWeights)
            ?: throw RuntimeException("Error loading model: " + aiGetErrorString())

        val meshParts = mutableListOf<MeshPart>()
        val embeddedTextures = mutableMapOf<String, Texture>()

        // Collect Bone Information
        val boneNames = mutableListOf<String>()
        val boneInfoMap = mutableMapOf<String, BoneInfo>()

        for (i in 0 until scene.mNumMeshes()) {
            val meshes = scene.mMeshes() ?: continue
            val mesh = AIMesh.create(meshes.get(i))
            for (b in 0 until mesh.mNumBones()) {
                val bones = mesh.mBones() ?: continue
                val bone = AIBone.create(bones.get(b))
                val name = BoneNameMapper.map(bone.mName().dataString())
                if (!boneNames.contains(name)) {
                    boneNames.add(name)
                    val offsetMatrix = bone.mOffsetMatrix().toJomlMatrix()
                    boneInfoMap[name] = BoneInfo(boneNames.size - 1, offsetMatrix)
                }
            }
        }

        val rootTransform = Matrix4f()
        val rootNode = scene.mRootNode() ?: throw RuntimeException("Error loading model: " + aiGetErrorString())
        processNode(rootNode, scene, rootTransform, meshParts, embeddedTextures, filePath, boneInfoMap)

        // Build Skeleton Hierarchy
        val rootBone = buildHierarchy(rootNode, boneInfoMap)

        // Recalculate Inverse Bind Matrices (IBMs) to match our modified hierarchy (Scale/Rotation)
        // This ensures the skinning equation (BoneWorld * IBM) is Identity at Bind Pose.
        if (rootBone != null) {
            rootBone.calculateWorldTransforms(Matrix4f())

            // Helper to find bone by name (since Skeleton class isn't built yet)
            fun findBone(node: Bone, name: String): Bone? {
                if (node.name == name) return node
                for (child in node.children) {
                    val res = findBone(child, name)
                    if (res != null) return res
                }
                return null
            }

            boneInfoMap.forEach { (name, info) ->
                val bone = findBone(rootBone, name)
                if (bone != null) {
                    // IBM = Inverse(BindPoseWorld)
                    bone.worldTransform.invert(info.offsetMatrix)
                    // Also update the bone's own storage
                    bone.inverseBindMatrix.set(info.offsetMatrix)
                }
            }
        }

        val skeleton = if (rootBone != null) Skeleton(rootBone, boneNames.size) else null

        aiReleaseImport(scene)
        return TexturedModel(mesh = meshParts, skeleton = skeleton, path = filePath)
    }

    private fun buildHierarchy(aiNode: AINode, boneInfoMap: Map<String, BoneInfo>): Bone? {
        val name = BoneNameMapper.map(aiNode.mName().dataString())
        val boneInfo = boneInfoMap[name]

        val localTransform = aiNode.mTransformation().toJomlMatrix()

        val bone = Bone(boneInfo?.index ?: -1, name, localTransform)
        boneInfo?.let { bone.inverseBindMatrix.set(it.offsetMatrix) }

        for (i in 0 until aiNode.mNumChildren()) {
            val children = aiNode.mChildren() ?: continue
            val childAiNode = AINode.create(children.get(i))
            val childBone = buildHierarchy(childAiNode, boneInfoMap)
            if (childBone != null) {
                bone.addChild(childBone)
            }
        }

        if (bone.index != -1 || bone.children.isNotEmpty()) {
            return bone
        }
        return null
    }

    private fun processNode(
        node: AINode,
        scene: AIScene,
        parentTransform: Matrix4f,
        meshParts: MutableList<MeshPart>,
        embeddedTextures: MutableMap<String, Texture>,
        filePath: String,
        boneInfoMap: Map<String, BoneInfo>,
    ) {
        val nodeTransform = parentTransform.mul(node.mTransformation().toJomlMatrix(), Matrix4f())

        for (i in 0 until node.mNumMeshes()) {
            val nodeMeshes = node.mMeshes() ?: continue
            val meshIndex = nodeMeshes.get(i)
            val sceneMesh = scene.mMeshes() ?: continue
            val mesh = AIMesh.create(sceneMesh.get(meshIndex))
            meshParts.add(processMesh(mesh, scene, nodeTransform, embeddedTextures, filePath, boneInfoMap))
        }

        for (i in 0 until node.mNumChildren()) {
            val children = node.mChildren() ?: continue
            val child = AINode.create(children.get(i))
            processNode(child, scene, nodeTransform, meshParts, embeddedTextures, filePath, boneInfoMap)
        }
    }

    private fun processMesh(
        mesh: AIMesh,
        scene: AIScene,
        transform: Matrix4f,
        embeddedTextures: MutableMap<String, Texture>,
        filePath: String,
        boneInfoMap: Map<String, BoneInfo>
    ): MeshPart {
        val materialData = Material()
        
        val materialIndex = mesh.mMaterialIndex()
        if (materialIndex >= 0) {
            val materials = scene.mMaterials() ?: throw RuntimeException("Error loading model: " + aiGetErrorString())
            val material = AIMaterial.create(materials.get(materialIndex))

            materialData.baseColorTexture =
                loadMaterialTexture(scene, material, aiTextureType_DIFFUSE, filePath, embeddedTextures) ?:
                                           loadMaterialTexture(scene, material, aiTextureType_BASE_COLOR, filePath, embeddedTextures)

            materialData.normalMap =
                loadMaterialTexture(scene, material, aiTextureType_NORMALS, filePath, embeddedTextures)

            materialData.metallicRoughnessTexture =
                loadMaterialTexture(scene, material, aiTextureType_METALNESS, filePath, embeddedTextures) ?:
                                                   loadMaterialTexture(scene, material, aiTextureType_UNKNOWN, filePath, embeddedTextures)

            materialData.aoTexture =
                loadMaterialTexture(scene, material, aiTextureType_AMBIENT_OCCLUSION, filePath, embeddedTextures) ?:
                                    loadMaterialTexture(scene, material, aiTextureType_LIGHTMAP, filePath, embeddedTextures)

            materialData.emissiveTexture =
                loadMaterialTexture(scene, material, aiTextureType_EMISSIVE, filePath, embeddedTextures)
            
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
                try {
                    materialData.alphaMode = AlphaMode.valueOf(alphaModeString.dataString())
                } catch (e: Exception) {
                    materialData.alphaMode = AlphaMode.OPAQUE
                }
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
            val vVec = Vector3f(vertex.x(), vertex.y(), vertex.z())
            transform.transformPosition(vVec)
            vertices[v * 3] = vVec.x
            vertices[v * 3 + 1] = vVec.y
            vertices[v * 3 + 2] = vVec.z

            val norms = mesh.mNormals() ?: continue
            val normal = norms.get(v)
            val nVec = Vector3f(normal.x(), normal.y(), normal.z())
            transform.transformDirection(nVec)
            normals[v * 3] = nVec.x
            normals[v * 3 + 1] = nVec.y
            normals[v * 3 + 2] = nVec.z

            val meshTangents = mesh.mTangents()

            if (meshTangents != null) {
                val tangent = meshTangents.get(v)
                val tVec = Vector3f(tangent.x(), tangent.y(), tangent.z())
                transform.transformDirection(tVec)
                tangents[v * 3] = tVec.x
                tangents[v * 3 + 1] = tVec.y
                tangents[v * 3 + 2] = tVec.z
            }

            val meshColors = mesh.mColors(0)
            if (meshColors != null) {
                val color = meshColors.get(v)
                colors[v * 4] = color.r()
                colors[v * 4 + 1] = color.g()
                colors[v * 4 + 2] = color.b()
                colors[v * 4 + 3] = color.a()
            } else {
                colors[v * 4] = 1f; colors[v * 4 + 1] = 1f; colors[v * 4 + 2] = 1f; colors[v * 4 + 3] = 1f
            }

            var meshTexCoords = mesh.mTextureCoords(0)
            if (meshTexCoords != null) {
                val texCoord = meshTexCoords.get(v)
                texCoords[v * 2] = texCoord.x()
                texCoords[v * 2 + 1] = texCoord.y()
            }

            meshTexCoords = mesh.mTextureCoords(1)
            if (meshTexCoords != null) {
                val texCoord = meshTexCoords.get(v)
                texCoords1[v * 2] = texCoord.x()
                texCoords1[v * 2 + 1] = texCoord.y()
            }
        }

        // Process Bones for Joints/Weights
        val inverseBindMatrices = mutableListOf<Matrix4f>()
        for (b in 0 until mesh.mNumBones()) {
            val bones = mesh.mBones() ?: continue
            val bone = AIBone.create(bones.get(b))
            val name = BoneNameMapper.map(bone.mName().dataString())
            val boneIndex = boneInfoMap[name]?.index ?: b
            
            val ibm = Matrix4f(boneInfoMap[name]?.offsetMatrix ?: Matrix4f()) 
            inverseBindMatrices.add(ibm)
            
            for (w in 0 until bone.mNumWeights()) {
                val weight = bone.mWeights().get(w)
                val vertexId = weight.mVertexId()
                val weightValue = weight.mWeight()
                
                // Find empty slot in joints/weights for this vertex
                for (slot in 0 until 4) {
                    if (weights[vertexId * 4 + slot] == 0f) {
                        joints[vertexId * 4 + slot] = boneIndex
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

        val rawModel = vaoLoader.loadToVAO(
            vertices,
            texCoords,
            normals,
            indices,
            vertices,
            tangents,
            colors,
            texCoords1,
            joints,
            weights
        )

        return MeshPart(
            vertices,
            texCoords,
            texCoords1,
            normals,
            tangents,
            colors,
            joints,
            weights,
            indices,
            materialData,
            rawModel,
            inverseBindMatrices
        )
    }

    private fun loadMaterialTexture(
        scene: AIScene,
        material: AIMaterial,
        type: Int,
        modelPath: String,
        embeddedTextures: MutableMap<String, Texture>
    ): Texture? {
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
            val texs = scene.mTextures() ?: return null
            val tex = AITexture.create(texs.get(index))
            val texturePath = "Embedded::$modelPath::$index"
            
            if (!embeddedTextures.containsKey(texturePath)) {
                val originalBuffer = MemoryUtil.memByteBuffer(tex.pcData().address(), tex.mWidth())
                val copy = ByteBuffer.allocateDirect(originalBuffer.remaining())
                copy.put(originalBuffer)
                copy.flip()
                embeddedTextures[texturePath] = textureLoader.loadFromBuffer(copy)
            }
            embeddedTextures[texturePath]
        } else {
            embeddedTextures[p] = textureLoader.loadFromFile(p)
            embeddedTextures[p]
        }
    }
}
