package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.utils.BoneNameMapper
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Represents a single animation clip containing multiple channels (TRS tracks).
 */
@Serializable
class Animation(
    val name: String,
    val channels: List<AnimationChannel>,
    val duration: Float,
    val bindPoses: Map<String, @Contextual Matrix4f> = emptyMap()
) {
    private val correctionMatrices = mutableMapOf<String, @Contextual Matrix4f>()
    private var correctionsComputed = false

    @Contextual
    private val tempBonePos = Vector3f()
    @Contextual
    private val tempBoneScale = Vector3f()
    @Contextual
    private val tempBoneQuat = Quaternionf()

    fun computeCorrections(skeleton: Skeleton) {
        if (correctionsComputed) return

        skeleton.getAllBones().forEach { bone ->
            val animBind = bindPoses[bone.name]
            if (animBind != null) {
                // Correction = inverse(ModelBind) * AnimBind
                // This maps the Animation's bind space to the Model's bind space.
                val correction = Matrix4f()
                // inverse(ModelBind)
                bone.bindLocalTransform.invert(correction)
                // * AnimBind
                correction.mul(animBind)
                correctionMatrices[bone.name] = correction
            }
        }
        correctionsComputed = true
    }

    /**
     * Updates the [skeleton] based on the specified [time].
     */
    fun update(time: Float, skeleton: Skeleton) {
        if (!correctionsComputed) computeCorrections(skeleton)

        val loopTime = time % duration

        // Track which bones we have modified this frame to avoid resetting them multiple times
        // or to handle the composition correctly.
        // Actually, since channels are linear, we can just process them.
        // BUT to avoid the "compounding correction" issue, we must start from a clean state.
        // We can't easily reset ALL skeleton bones here efficiently without iterating the whole skeleton.
        // Optimization: Iterate only channels, identify unique bones.

        val affectedBones = mutableSetOf<Bone>()
        channels.forEach { channel ->
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            skeleton.getBoneByName(mappedName)?.let { affectedBones.add(it) }
        }

        // 1. Reset affected bones to their Bind Pose (Model Space)
        // This ensures we don't accumulate corrections or previous frame's data endlessly
        // and provides the default values for missing channels (e.g. if Anim has Rot but no Trans).
        affectedBones.forEach { bone ->
            bone.localTransform.set(bone.bindLocalTransform)
        }

        // 2. Apply Animation Channels (Overwrite Bind Pose values with Animation values)
        // Note: This temporarily puts `bone.localTransform` into "Animation Space" (mixed with Model defaults if missing)
        val pos = Vector3f()
        val rot = Quaternionf()
        val scale = Vector3f()

        for (channel in channels) {
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            val bone = skeleton.getBoneByName(mappedName) ?: continue

            // Read current (Bind Pose or partially animated) state
            bone.localTransform.getTranslation(pos)
            bone.localTransform.getUnnormalizedRotation(rot)
            bone.localTransform.getScale(scale)

            when (channel.path) {
                AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, pos)
                AnimationPath.ROTATION -> channel.sampler.sampleQuaternionf(loopTime, rot)
                AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, scale)
            }

            bone.localTransform.translationRotateScale(pos, rot, scale)
        }

        affectedBones.forEach { bone ->
            val animBind = bindPoses[bone.name]
            if (animBind != null) {
                bone.localTransform.set(animBind)
            } else {
                bone.localTransform.set(bone.bindLocalTransform)
            }
        }

        // REVISED Step 2: Apply Channels (Overwriting Anim Bind Pose)
        for (channel in channels) {
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            val bone = skeleton.getBoneByName(mappedName) ?: continue

            bone.localTransform.getTranslation(pos)
            bone.localTransform.getUnnormalizedRotation(rot)
            bone.localTransform.getScale(scale)

            when (channel.path) {
                AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, pos)
                AnimationPath.ROTATION -> channel.sampler.sampleQuaternionf(loopTime, rot)
                AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, scale)
            }
            bone.localTransform.translationRotateScale(pos, rot, scale)
        }

        affectedBones.forEach { bone ->
            val animBind = bindPoses[bone.name] ?: return@forEach

            val delta = Matrix4f()
            animBind.invert(delta)
            delta.mul(bone.localTransform)

            // Final = M_bind * Delta
            val finalMat = Matrix4f(bone.bindLocalTransform)
            finalMat.mul(delta)

            bone.localTransform.set(finalMat)
        }
    }

    /**
     * Updates the [skeleton] by blending.
     */
    fun updateBlended(time: Float, skeleton: Skeleton, alpha: Float) {
        if (!correctionsComputed) computeCorrections(skeleton)
        val loopTime = time % duration

        val affectedBones = mutableSetOf<Bone>()
        channels.forEach { channel ->
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            skeleton.getBoneByName(mappedName)?.let { affectedBones.add(it) }
        }

        // 1. Reset to Anim Bind Pose
        affectedBones.forEach { bone ->
            val animBind = bindPoses[bone.name]
            if (animBind != null) {
                bone.localTransform.set(animBind)
            } else {
                bone.localTransform.set(bone.bindLocalTransform)
            }
        }

        // Store original transforms for blending
        val originalTransforms = mutableMapOf<String, Matrix4f>()
        affectedBones.forEach { bone ->
            originalTransforms[bone.name] = Matrix4f(bone.localTransform)
        }

        // Apply Animation Channels to get Target Animation State
        for (channel in channels) {
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            val bone = skeleton.getBoneByName(mappedName) ?: continue

            // Read current (Animation Bind Pose) state
            bone.localTransform.getTranslation(tempBonePos)
            bone.localTransform.getUnnormalizedRotation(tempBoneQuat)
            bone.localTransform.getScale(tempBoneScale)

            when (channel.path) {
                AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, tempBonePos)
                AnimationPath.ROTATION -> channel.sampler.sampleQuaternionf(loopTime, tempBoneQuat)
                AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, tempBoneScale)
            }

            bone.localTransform.translationRotateScale(tempBonePos, tempBoneQuat, tempBoneScale)
        }

        // Apply Corrections to convert to Model Space
        affectedBones.forEach { bone ->
            val animBind = bindPoses[bone.name] ?: return@forEach
            // Current `bone.localTransform` is A_current (from this animation).

            // Delta = inv(A_bind) * A_current
            val delta = Matrix4f()
            animBind.invert(delta)
            delta.mul(bone.localTransform)  // delta = inv(animBind) * bone.localTransform

            // Final = M_bind * Delta
            val finalMat = Matrix4f(bone.bindLocalTransform)  // M_bind
            finalMat.mul(delta)  // finalMat = M_bind * inv(animBind) * A_current

            // Now bone.localTransform contains the corrected animation state
            val correctedAnimState = Matrix4f(finalMat)

            // Blend between original state and corrected animation state
            val originalState = originalTransforms[bone.name] ?: bone.bindLocalTransform

            // Extract translation, rotation, and scale from both matrices
            val origTranslation = Vector3f()
            val origRotation = Quaternionf()
            val origScale = Vector3f()
            originalState.getTranslation(origTranslation)
            originalState.getUnnormalizedRotation(origRotation)
            originalState.getScale(origScale)

            val animTranslation = Vector3f()
            val animRotation = Quaternionf()
            val animScale = Vector3f()
            correctedAnimState.getTranslation(animTranslation)
            correctedAnimState.getUnnormalizedRotation(animRotation)
            correctedAnimState.getScale(animScale)

            // Interpolate each component
            val blendedTranslation = Vector3f(origTranslation).lerp(animTranslation, alpha)
            val blendedRotation = Quaternionf(origRotation).slerp(animRotation, alpha)
            val blendedScale = Vector3f(origScale).lerp(animScale, alpha)

            // Recompose the matrix
            bone.localTransform.translationRotateScale(blendedTranslation, blendedRotation, blendedScale)
        }

    }
}

