package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.animation.Animation
import com.pafoid.skate.engine.animation.AnimationChannel
import com.pafoid.skate.engine.animation.AnimationPath
import com.pafoid.skate.engine.animation.AnimationSampler
import com.pafoid.skate.engine.animation.InterpolationType
import com.pafoid.skate.engine.utils.BoneNameMapper
import com.pafoid.skate.engine.utils.printMetadata
import org.joml.Quaternionf
import org.lwjgl.assimp.AIAnimation
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AINodeAnim
import org.lwjgl.assimp.Assimp.aiGetErrorString
import org.lwjgl.assimp.Assimp.aiImportFile
import org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices
import org.lwjgl.assimp.Assimp.aiProcess_LimitBoneWeights
import org.lwjgl.assimp.Assimp.aiProcess_Triangulate
import org.lwjgl.assimp.Assimp.aiReleaseImport

class AnimationLoader {

    fun loadAnimations(filePath: String): List<Animation> {
        val scene = aiImportFile(
            filePath,
            aiProcess_Triangulate or aiProcess_JoinIdenticalVertices or aiProcess_LimitBoneWeights
        )
            ?: throw RuntimeException("Error loading animations: " + aiGetErrorString())

        scene.printMetadata("Animation Scene")

        var unitScale = 1.0f
        if (filePath.contains("skateboard", ignoreCase = true)) {
            unitScale = 0.0017f
        } else if (filePath.contains("characters", ignoreCase = true) && filePath.endsWith(".fbx", ignoreCase = true)) {
            unitScale = 0.01f
        }

        println("Inspecting Bone Hierarchy for: $filePath")

        // Try to identify root bone for motion zeroing
        var rootBoneName: String? = null
        val rootNode = scene.mRootNode()

        if (rootNode != null && rootNode.mNumChildren() > 0) {
            val children = rootNode.mChildren()
            if (children != null) {
                // Heuristic: First child is usually the Armature/Root.
                // We want the actual Hip bone, which might be the first child of the Armature, or the first child itself.
                // Let's look for "Hips" specifically first.

                fun findHips(node: AINode): String? {
                    val name = BoneNameMapper.map(node.mName().dataString())
                    if (name.equals("Hips", ignoreCase = true)) return name

                    for (i in 0 until node.mNumChildren()) {
                        val child = AINode.create(node.mChildren()!!.get(i))
                        val res = findHips(child)
                        if (res != null) return res
                    }
                    return null
                }

                rootBoneName = findHips(rootNode)

                // Fallback: Use the first child's name if it's not the scene root itself
                if (rootBoneName == null) {
                    val firstChild = AINode.create(children.get(0))
                    rootBoneName = BoneNameMapper.map(firstChild.mName().dataString())
                }
            }
        }

        val animations = mutableListOf<Animation>()
        for (i in 0 until scene.mNumAnimations()) {
            val anims = scene.mAnimations() ?: continue
            val aiAnim = AIAnimation.create(anims.get(i))
            animations.add(processAnimation(aiAnim, unitScale, rootBoneName))
        }

        aiReleaseImport(scene)
        return animations
    }

    fun processAnimation(aiAnim: AIAnimation, scale: Float = 1.0f, rootNodeName: String? = null): Animation {
        val name = aiAnim.mName().dataString()
        val duration = aiAnim.mDuration().toFloat()
        val ticksPerSecond = if (aiAnim.mTicksPerSecond() != 0.0) aiAnim.mTicksPerSecond().toFloat() else 60f
        val durationInSeconds = duration / ticksPerSecond


        val channels = mutableListOf<AnimationChannel>()
        for (i in 0 until aiAnim.mNumChannels()) {
            val anims = aiAnim.mChannels() ?: continue
            val aiChannel = AINodeAnim.create(anims.get(i))
            val originalName = aiChannel.mNodeName().dataString()
            val nodeName = BoneNameMapper.map(originalName)
            val isRoot = (rootNodeName != null && nodeName.equals(rootNodeName, ignoreCase = true)) || nodeName.equals(
                "Hips",
                ignoreCase = true
            )
            println("AssimpLoader: Found Node '$nodeName' (Original: ${originalName}) isRoot: $isRoot")

            // Translation
            // For root bones, we zero out translation to keep animations in place
            if (aiChannel.mNumPositionKeys() > 0) {
                val times = FloatArray(aiChannel.mNumPositionKeys())
                val values = FloatArray(aiChannel.mNumPositionKeys() * 3)

                for (k in 0 until aiChannel.mNumPositionKeys()) {
                    val key = aiChannel.mPositionKeys()?.get(k) ?: continue
                    times[k] = key.mTime().toFloat() / ticksPerSecond

                    values[k * 3] = if (isRoot) 0f else key.mValue().x() * scale
                    values[k * 3 + 1] = key.mValue().y() * scale
                    values[k * 3 + 2] = if (isRoot) 0f else key.mValue().z() * scale
                }

                channels.add(
                    AnimationChannel(
                        AnimationSampler(times, values, InterpolationType.LINEAR, 3),
                        nodeName,
                        AnimationPath.TRANSLATION,
                        scale
                    )
                )
            }

            // Rotation
            if (aiChannel.mNumRotationKeys() > 0) {
                val times = FloatArray(aiChannel.mNumRotationKeys())
                val values = FloatArray(aiChannel.mNumRotationKeys() * 4)
                for (k in 0 until aiChannel.mNumRotationKeys()) {
                    val keys = aiChannel.mRotationKeys() ?: continue
                    val key = keys.get(k)
                    times[k] = key.mTime().toFloat() / ticksPerSecond

                    val q = Quaternionf(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w())

                    values[k * 4] = q.x
                    values[k * 4 + 1] = q.y
                    values[k * 4 + 2] = q.z
                    values[k * 4 + 3] = q.w
                }
                val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 4)
                channels.add(AnimationChannel(sampler, nodeName, AnimationPath.ROTATION))
            }

            // Scale (DISABLED FOR SAFETY)
            /*
            if (aiChannel.mNumScalingKeys() > 0) {
                val times = FloatArray(aiChannel.mNumScalingKeys())
                val values = FloatArray(aiChannel.mNumScalingKeys() * 3)
                for (k in 0 until aiChannel.mNumScalingKeys()) {
                    val keys = aiChannel.mScalingKeys() ?: continue
                    val key = keys.get(k)
                    times[k] = key.mTime().toFloat() / ticksPerSecond
                    values[k * 3] = key.mValue().x()
                    values[k * 3 + 1] = key.mValue().y()
                    values[k * 3 + 2] = key.mValue().z()
                }
                val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 3)
                channels.add(AnimationChannel(sampler, nodeName, AnimationPath.SCALE))
            }
            */
        }

        return Animation(name, channels, durationInSeconds)
    }

}