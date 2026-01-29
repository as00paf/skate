package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.animation.Animation
import com.pafoid.skate.engine.animation.AnimationChannel
import com.pafoid.skate.engine.animation.AnimationPath
import com.pafoid.skate.engine.animation.AnimationSampler
import com.pafoid.skate.engine.animation.InterpolationType
import com.pafoid.skate.engine.animation.Joint
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.render.VAOLoader
import org.joml.Matrix4f
import org.lwjgl.assimp.*
import org.lwjgl.assimp.Assimp.*
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.IntBuffer


class AssimpLoader {

    fun preLoadModel(filePath: String): PreLoadedModel {
        // TODO: handle error
        val scene = aiImportFile(filePath, aiProcess_Triangulate or aiProcess_FlipUVs or aiProcess_JoinIdenticalVertices or aiProcess_CalcTangentSpace or aiProcess_LimitBoneWeights)
            ?: throw RuntimeException("Error loading model: " + aiGetErrorString())

        val meshParts = mutableListOf<PreLoadedMeshPart>()
        val embeddedTextures = mutableMapOf<String, ByteBuffer>()

        // Collect Bone Information
        val boneNames = mutableListOf<String>()
        val boneInfoMap = mutableMapOf<String, BoneInfo>()

        // TODO: fix nullability
        for (i in 0 until scene.mNumMeshes()) {
            val mesh = AIMesh.create(scene.mMeshes()!!.get(i))
            for (b in 0 until mesh.mNumBones()) {
                val bone = AIBone.create(mesh.mBones()!!.get(b))
                val name = bone.mName().dataString()
                if (!boneNames.contains(name)) {
                    boneNames.add(name)
                    boneInfoMap[name] = BoneInfo(boneNames.size - 1, toJomlMatrix(bone.mOffsetMatrix()))
                }
            }
        }

        // Task 3.1: Unit Normalization
        var unitScale = 1.0f
        
        if (filePath.contains("skateboard", ignoreCase = true)) {
            unitScale = 0.0017f // Results in ~0.8m length for skateboard_free_model.glb
        }

        // TODO: fix nullability
        val rootTransform = Matrix4f().scale(unitScale)
        processNode(scene.mRootNode()!!, scene, rootTransform, meshParts, embeddedTextures, filePath, boneInfoMap)

        // Build Skeleton Hierarchy
        val rootJoint = buildHierarchy(scene.mRootNode()!!, boneInfoMap)
        val skeleton = if (rootJoint != null) Skeleton(rootJoint, boneNames.size) else null

        // Load Animations
        val animations = mutableListOf<Animation>()
        for (i in 0 until scene.mNumAnimations()) {
            val aiAnim = AIAnimation.create(scene.mAnimations()!!.get(i))
            animations.add(processAnimation(aiAnim))
        }

        aiReleaseImport(scene)
        return PreLoadedModel(meshParts, skeleton, animations)
    }

    private fun buildHierarchy(aiNode: AINode, boneInfoMap: Map<String, BoneInfo>): Joint? {
        val name = aiNode.mName().dataString()
        val boneInfo = boneInfoMap[name]
        
        val joint = Joint(boneInfo?.index ?: -1, name, toJomlMatrix(aiNode.mTransformation()))
        boneInfo?.let { joint.inverseBindMatrix.set(it.offsetMatrix) }

        for (i in 0 until aiNode.mNumChildren()) {
            val childAiNode = AINode.create(aiNode.mChildren()!!.get(i))
            val childJoint = buildHierarchy(childAiNode, boneInfoMap)
            if (childJoint != null) {
                joint.addChild(childJoint)
            }
        }
        
        if (joint.index != -1 || joint.children.isNotEmpty()) {
            return joint
        }
        return null
    }

    private fun processAnimation(aiAnim: AIAnimation): Animation {
        val name = aiAnim.mName().dataString()
        val duration = aiAnim.mDuration().toFloat()
        val ticksPerSecond = if (aiAnim.mTicksPerSecond() != 0.0) aiAnim.mTicksPerSecond().toFloat() else 25f
        val durationInSeconds = duration / ticksPerSecond

        val channels = mutableListOf<AnimationChannel>()
        for (i in 0 until aiAnim.mNumChannels()) {
            val aiChannel = AINodeAnim.create(aiAnim.mChannels()!!.get(i))
            val nodeName = aiChannel.mNodeName().dataString()

            //TODO: extract
            // Translation
            if (aiChannel.mNumPositionKeys() > 0) {
                val times = FloatArray(aiChannel.mNumPositionKeys())
                val values = FloatArray(aiChannel.mNumPositionKeys() * 3)
                for (k in 0 until aiChannel.mNumPositionKeys()) {
                    val key = aiChannel.mPositionKeys()!!.get(k)
                    times[k] = key.mTime().toFloat() / ticksPerSecond
                    values[k * 3] = key.mValue().x()
                    values[k * 3 + 1] = key.mValue().y()
                    values[k * 3 + 2] = key.mValue().z()
                }
                val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 3)
                channels.add(AnimationChannel(sampler, nodeName, AnimationPath.TRANSLATION))
            }

            // Rotation
            if (aiChannel.mNumRotationKeys() > 0) {
                val times = FloatArray(aiChannel.mNumRotationKeys())
                val values = FloatArray(aiChannel.mNumRotationKeys() * 4)
                for (k in 0 until aiChannel.mNumRotationKeys()) {
                    val key = aiChannel.mRotationKeys()!!.get(k)
                    times[k] = key.mTime().toFloat() / ticksPerSecond
                    values[k * 4] = key.mValue().x()
                    values[k * 4 + 1] = key.mValue().y()
                    values[k * 4 + 2] = key.mValue().z()
                    values[k * 4 + 3] = key.mValue().w()
                }
                val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 4)
                channels.add(AnimationChannel(sampler, nodeName, AnimationPath.ROTATION))
            }

            // Scale
            if (aiChannel.mNumScalingKeys() > 0) {
                val times = FloatArray(aiChannel.mNumScalingKeys())
                val values = FloatArray(aiChannel.mNumScalingKeys() * 3)
                for (k in 0 until aiChannel.mNumScalingKeys()) {
                    val key = aiChannel.mScalingKeys()!!.get(k)
                    times[k] = key.mTime().toFloat() / ticksPerSecond
                    values[k * 3] = key.mValue().x()
                    values[k * 3 + 1] = key.mValue().y()
                    values[k * 3 + 2] = key.mValue().z()
                }
                val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 3)
                channels.add(AnimationChannel(sampler, nodeName, AnimationPath.SCALE))
            }
        }

        return Animation(name, channels, durationInSeconds)
    }

    private fun processNode(node: AINode, scene: AIScene, parentTransform: Matrix4f, meshParts: MutableList<PreLoadedMeshPart>, embeddedTextures: MutableMap<String, ByteBuffer>, filePath: String, boneInfoMap: Map<String, BoneInfo>) {
        val nodeTransform = parentTransform.mul(toJomlMatrix(node.mTransformation()), Matrix4f())

        for (i in 0 until node.mNumMeshes()) {
            val meshIndex = node.mMeshes()!!.get(i)
            val mesh = AIMesh.create(scene.mMeshes()!!.get(meshIndex))
            meshParts.add(processMesh(mesh, scene, nodeTransform, embeddedTextures, filePath, boneInfoMap))
        }

        for (i in 0 until node.mNumChildren()) {
            val child = AINode.create(node.mChildren()!!.get(i))
            processNode(child, scene, nodeTransform, meshParts, embeddedTextures, filePath, boneInfoMap)
        }
    }

    private fun processMesh(mesh: AIMesh, scene: AIScene, transform: Matrix4f, embeddedTextures: MutableMap<String, ByteBuffer>, filePath: String, boneInfoMap: Map<String, BoneInfo>): PreLoadedMeshPart {
        val materialData = com.pafoid.skate.engine.models.Material()
        
        val materialIndex = mesh.mMaterialIndex()
        if (materialIndex >= 0) {
            val material = AIMaterial.create(scene.mMaterials()!!.get(materialIndex))
            
            materialData.baseColorPath = loadMaterialTexture(scene, material, aiTextureType_DIFFUSE, filePath, embeddedTextures) ?: 
                                           loadMaterialTexture(scene, material, aiTextureType_BASE_COLOR, filePath, embeddedTextures)
            
            materialData.normalMapPath = loadMaterialTexture(scene, material, aiTextureType_NORMALS, filePath, embeddedTextures)
            
            materialData.metallicRoughnessPath = loadMaterialTexture(scene, material, aiTextureType_METALNESS, filePath, embeddedTextures) ?:
                                                   loadMaterialTexture(scene, material, aiTextureType_UNKNOWN, filePath, embeddedTextures)
            
            materialData.aoPath = loadMaterialTexture(scene, material, aiTextureType_AMBIENT_OCCLUSION, filePath, embeddedTextures) ?:
                                    loadMaterialTexture(scene, material, aiTextureType_LIGHTMAP, filePath, embeddedTextures)
            
            materialData.emissivePath = loadMaterialTexture(scene, material, aiTextureType_EMISSIVE, filePath, embeddedTextures)
            
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
        val inverseBindMatrices = mutableListOf<Matrix4f>()
        for (b in 0 until mesh.mNumBones()) {
            val bone = AIBone.create(mesh.mBones()!!.get(b))
            val name = bone.mName().dataString()
            val boneIndex = boneInfoMap[name]?.index ?: b
            
            inverseBindMatrices.add(toJomlMatrix(bone.mOffsetMatrix()))
            
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

        return PreLoadedMeshPart(vertices, texCoords, texCoords1, normals, tangents, colors, joints, weights, indices, materialData, GL11.GL_TRIANGLES, embeddedTextures, inverseBindMatrices)
    }

    private fun toJomlMatrix(aiMatrix: AIMatrix4x4): Matrix4f {
        return Matrix4f(
            aiMatrix.a1(), aiMatrix.b1(), aiMatrix.c1(), aiMatrix.d1(),
            aiMatrix.a2(), aiMatrix.b2(), aiMatrix.c2(), aiMatrix.d2(),
            aiMatrix.a3(), aiMatrix.b3(), aiMatrix.c3(), aiMatrix.d3(),
            aiMatrix.a4(), aiMatrix.b4(), aiMatrix.c4(), aiMatrix.d4()
        )
    }

    private fun loadMaterialTexture(scene: AIScene, material: AIMaterial, type: Int, modelPath: String, embeddedTextures: MutableMap<String, ByteBuffer>): String? {
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
            
            if (!embeddedTextures.containsKey(texturePath)) {
                val originalBuffer = MemoryUtil.memByteBuffer(tex.pcData().address(), tex.mWidth())
                val copy = ByteBuffer.allocateDirect(originalBuffer.remaining())
                copy.put(originalBuffer)
                copy.flip()
                embeddedTextures[texturePath] = copy
            }
            texturePath
        } else {
            File(modelPath).parentFile.resolve(p).path
        }
    }

    fun loadModel(filePath: String, loader: VAOLoader): List<LoadedMeshPart> {
        val preLoaded = preLoadModel(filePath)
        return preLoaded.parts.map { p ->
            val model = loader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1, p.joints, p.weights)
            LoadedMeshPart(model, p.material, p.inverseBindMatrices)
        }
    }
}