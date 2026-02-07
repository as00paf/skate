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
    private val tempJointPos = Vector3f()
    @Contextual
    private val tempJointScale = Vector3f()
    @Contextual
    private val tempJointQuat = Quaternionf()

    fun computeCorrections(skeleton: Skeleton) {
        if (correctionsComputed) return
        
        skeleton.getAllJoints().forEach { joint ->
            val animBind = bindPoses[joint.name]
            if (animBind != null) {
                // Correction = inverse(ModelBind) * AnimBind
                // This maps the Animation's bind space to the Model's bind space.
                val correction = Matrix4f()
                // inverse(ModelBind)
                joint.bindLocalTransform.invert(correction)
                // * AnimBind
                correction.mul(animBind)
                correctionMatrices[joint.name] = correction
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
        
        // Track which joints we have modified this frame to avoid resetting them multiple times
        // or to handle the composition correctly.
        // Actually, since channels are linear, we can just process them.
        // BUT to avoid the "compounding correction" issue, we must start from a clean state.
        // We can't easily reset ALL skeleton joints here efficiently without iterating the whole skeleton.
        // Optimization: Iterate only channels, identify unique joints.
        
        val affectedJoints = mutableSetOf<Joint>()
        channels.forEach { channel ->
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            skeleton.getJointByName(mappedName)?.let { affectedJoints.add(it) }
        }
        
        // 1. Reset affected joints to their Bind Pose (Model Space)
        // This ensures we don't accumulate corrections or previous frame's data endlessly
        // and provides the default values for missing channels (e.g. if Anim has Rot but no Trans).
        affectedJoints.forEach { joint ->
            joint.localTransform.set(joint.bindLocalTransform)
        }
        
        // 2. Apply Animation Channels (Overwrite Bind Pose values with Animation values)
        // Note: This temporarily puts `joint.localTransform` into "Animation Space" (mixed with Model defaults if missing)
        val pos = Vector3f()
        val rot = Quaternionf()
        val scale = Vector3f()
        
        for (channel in channels) {
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            val joint = skeleton.getJointByName(mappedName) ?: continue
            
            // Read current (Bind Pose or partially animated) state
            joint.localTransform.getTranslation(pos)
            joint.localTransform.getUnnormalizedRotation(rot)
            joint.localTransform.getScale(scale)
            
            when (channel.path) {
                AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, pos)
                AnimationPath.ROTATION -> channel.sampler.sampleQuaternionf(loopTime, rot)
                AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, scale)
            }
            
            joint.localTransform.translationRotateScale(pos, rot, scale)
        }

        affectedJoints.forEach { joint ->
            val animBind = bindPoses[joint.name]
            if (animBind != null) {
                joint.localTransform.set(animBind)
            } else {
                joint.localTransform.set(joint.bindLocalTransform)
            }
        }
        
        // REVISED Step 2: Apply Channels (Overwriting Anim Bind Pose)
        for (channel in channels) {
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            val joint = skeleton.getJointByName(mappedName) ?: continue
            
            joint.localTransform.getTranslation(pos)
            joint.localTransform.getUnnormalizedRotation(rot)
            joint.localTransform.getScale(scale)
            
            when (channel.path) {
                AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, pos)
                AnimationPath.ROTATION -> channel.sampler.sampleQuaternionf(loopTime, rot)
                AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, scale)
            }
            joint.localTransform.translationRotateScale(pos, rot, scale)
        }

        affectedJoints.forEach { joint ->
            val animBind = bindPoses[joint.name] ?: return@forEach

            val delta = Matrix4f()
            animBind.invert(delta)
            delta.mul(joint.localTransform)
            
            // Final = M_bind * Delta
            val finalMat = Matrix4f(joint.bindLocalTransform)
            finalMat.mul(delta)
            
            joint.localTransform.set(finalMat)
        }
    }

    /**
     * Updates the [skeleton] by blending.
     */
    fun updateBlended(time: Float, skeleton: Skeleton, alpha: Float) {
        if (!correctionsComputed) computeCorrections(skeleton)
        val loopTime = time % duration
        
        val affectedJoints = mutableSetOf<Joint>()
        channels.forEach { channel ->
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            skeleton.getJointByName(mappedName)?.let { affectedJoints.add(it) }
        }
        
        // 1. Reset to Anim Bind Pose
        affectedJoints.forEach { joint ->
            val animBind = bindPoses[joint.name]
            if (animBind != null) {
                joint.localTransform.set(animBind)
            } else {
                joint.localTransform.set(joint.bindLocalTransform)
            }
        }

        // Store original transforms for blending
        val originalTransforms = mutableMapOf<String, Matrix4f>()
        affectedJoints.forEach { joint ->
            originalTransforms[joint.name] = Matrix4f(joint.localTransform)
        }

        // Apply Animation Channels to get Target Animation State
        for (channel in channels) {
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            val joint = skeleton.getJointByName(mappedName) ?: continue

            // Read current (Animation Bind Pose) state
            joint.localTransform.getTranslation(tempJointPos)
            joint.localTransform.getUnnormalizedRotation(tempJointQuat)
            joint.localTransform.getScale(tempJointScale)

            when (channel.path) {
                AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, tempJointPos)
                AnimationPath.ROTATION -> channel.sampler.sampleQuaternionf(loopTime, tempJointQuat)
                AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, tempJointScale)
            }

            joint.localTransform.translationRotateScale(tempJointPos, tempJointQuat, tempJointScale)
        }

        // Apply Corrections to convert to Model Space
        affectedJoints.forEach { joint ->
            val animBind = bindPoses[joint.name] ?: return@forEach
            // Current `joint.localTransform` is A_current (from this animation).

            // Delta = inv(A_bind) * A_current
            val delta = Matrix4f()
            animBind.invert(delta)
            delta.mul(joint.localTransform)  // delta = inv(animBind) * joint.localTransform

            // Final = M_bind * Delta
            val finalMat = Matrix4f(joint.bindLocalTransform)  // M_bind
            finalMat.mul(delta)  // finalMat = M_bind * inv(animBind) * A_current

            // Now joint.localTransform contains the corrected animation state
            val correctedAnimState = Matrix4f(finalMat)

            // Blend between original state and corrected animation state
            val originalState = originalTransforms[joint.name] ?: joint.bindLocalTransform
            
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
            joint.localTransform.translationRotateScale(blendedTranslation, blendedRotation, blendedScale)
        }

    }
}

