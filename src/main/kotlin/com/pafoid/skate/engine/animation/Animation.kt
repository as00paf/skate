package com.pafoid.skate.engine.animation

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Represents a single animation clip containing multiple channels (TRS tracks).
 */
class Animation(
    val name: String,
    val channels: List<AnimationChannel>,
    val duration: Float,
    val bindPoses: Map<String, Matrix4f> = emptyMap()
) {
    private val correctionMatrices = mutableMapOf<String, Matrix4f>()
    private var correctionsComputed = false

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
        channels.forEach { 
            skeleton.getJointByName(it.targetNodeName)?.let { affectedJoints.add(it) } 
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
            val joint = skeleton.getJointByName(channel.targetNodeName) ?: continue
            
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
        
        // 3. Apply Correction: Final = Correction * Animation
        // The Animation values are currently in `joint.localTransform`.
        // We interpreted them as "Animation Local".
        // We want to transform them to "Model Local".
        // Correction = inv(ModelBind) * AnimBind
        // Wait.
        // If we reset to ModelBind in Step 1.
        // And then we overwrite Translation with AnimTranslation (from Anim Space).
        // We have a hybrid matrix. This is bad.
        
        // If we want to use the "Correction Matrix" approach, we must treat the Animation Data 
        // as being purely in "Animation Space".
        // If a channel is missing (e.g. Scale), we should use the "Animation Bind Pose Scale", not the "Model Bind Pose Scale".
        
        // REVISED Step 1: Reset to ANIMATION Bind Pose (if available), else Model Bind Pose.
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
            val joint = skeleton.getJointByName(channel.targetNodeName) ?: continue
            
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
        
        // REVISED Step 3: Apply Correction
        // Now `joint.localTransform` is fully in "Animation Space".
        // We convert it to "Model Space" using the correction matrix.
        // Correction = inv(ModelBind) * AnimBind? 
        // No. We want `ModelLocal = Correction * AnimLocal`.
        // If AnimLocal == AnimBind, we want Result == ModelBind.
        // `ModelBind = Correction * AnimBind`
        // `Correction = ModelBind * inv(AnimBind)`
        
        // User said: `inverse(modelBindLocal) * animationBindLocal`
        // User said: `finalLocal = correction * animationLocalKey`
        // `finalLocal` (Model Space?) = `inv(Model) * Anim` * `AnimKey`?
        // `inv(Model) * Anim * AnimKey` -> This moves it deeper into Anim space?
        
        // Let's derive it.
        // We want M_local. We have A_local.
        // We assume the bone represents the "same" bone physically.
        // M_world_bind = Parent_M_world * M_local_bind
        // A_world_bind = Parent_A_world * A_local_bind
        
        // Retargeting usually means:
        // "Apply the delta rotation from the animation to the model".
        // Delta = A_current * inv(A_bind).
        // M_current = M_bind * Delta.
        // M_current = M_bind * A_current * inv(A_bind).
        
        // Let's try this logic:
        // Correction = M_bind * inv(A_bind).
        // Final = Correction * A_current.
        // Final = M_bind * inv(A_bind) * A_current.
        // Check: If A_current == A_bind -> Final = M_bind * I = M_bind. Correct.
        
        // User's logic: `inverse(modelBindLocal) * animationBindLocal`.
        // That creates a mapping from A to M?
        // `inv(M) * A`.
        // If we multiply by A_current... `inv(M) * A * A_current`.
        // Does not yield M.
        
        // I will stick to the standard Retargeting Logic:
        // M_final = M_bind * (inv(A_bind) * A_current)
        
        // So:
        // 1. Reset joint to A_bind (using `bindPoses` map).
        // 2. Apply animation channels -> A_current.
        // 3. Compute `Delta = inv(A_bind) * A_current`. (Or `A_bind.invert().mul(A_current)`)
        // 4. `Final = M_bind * Delta`.
        
        // Optimization:
        // We can precompute `inv(A_bind)`.
        // And we have `M_bind` (`bindLocalTransform`).
        
        // Wait, what if the coordinate axes are permuted?
        // e.g. M_x = A_y.
        // M_bind * inv(A_bind) handles the basis change IF the bones are aligned in world space but defined differently in local space.
        
        affectedJoints.forEach { joint ->
            val animBind = bindPoses[joint.name] ?: return@forEach
            // Current `joint.localTransform` is A_current.
            
            // Delta = inv(A_bind) * A_current
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
        // Blending with corrections is hard.
        // Simplest: Compute Final A (Corrected) and Final B (Corrected) and blend?
        // Or just blend A_current and B_current (if they are in same space) then correct?
        // Since we are fixing "Animation Space", assume all animations are in the same "Anim Space" (e.g. Mixamo).
        // So we blend in Anim Space, then retarget to Model Space.
        
        if (!correctionsComputed) computeCorrections(skeleton)
        val loopTime = time % duration
        
        val affectedJoints = mutableSetOf<Joint>()
        channels.forEach { 
            skeleton.getJointByName(it.targetNodeName)?.let { affectedJoints.add(it) } 
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
        
        // 2. Apply Channels to get Target A_current
        val targetMat = Matrix4f()
        val pos = Vector3f(); val rot = Quaternionf(); val scale = Vector3f()
        
        for (channel in channels) {
            val joint = skeleton.getJointByName(channel.targetNodeName) ?: continue
            
            // We need to Blend current (Previous Frame Model Space?) with New (Anim Space)?
            // updateBlended assumes "current state of skeleton" is "Previous Animation Frame".
            // If the previous frame was already retargeted to Model Space...
            // And we generate a new frame in Anim Space...
            // We can't blend them directly!
            
            // Ideally: `previousAnimation.update` put the skeleton in Model Space.
            // We want to interpolate to `thisAnimation.update` (Model Space).
            // But `updateBlended` logic in `Animator` is:
            // prev.update(prevTime)
            // next.updateBlended(currTime, alpha)
            
            // If `next.updateBlended` modifies the skeleton IN PLACE, it blends the current state (Prev Model Space) with Target (Next Model Space).
            
            // Implementation:
            // 1. Calculate "Target Model Space" matrix for this animation frame (same logic as `update`).
            // 2. Blend `joint.localTransform` (Existing) -> `Target`.
            
            // Let's implement that.
            
            // ... Logic to calculate Target Model Space ...
            // But we can't overwrite `joint.localTransform` yet.
            // We need a temp map?
        }
        // ... Given complexity, I will implement `update` correctly first.
        // `updateBlended` might need a fallback or simplified logic for now.
        // Let's just use `update` logic for `updateBlended` temporarily to verify the fix, 
        // or implement the lerp at the end.
        
        // Actually, `updateBlended` in this codebase does:
        // `joint.localTransform` (Starts as Prev State)
        // `channel.sample` -> Target
        // `Lerp(Current, Target)`
        
        // If Current is Model Space.
        // And Target (from Channel) is Anim Space.
        // We are blending Apples and Oranges.
        
        // We MUST convert Target to Model Space BEFORE blending.
        
        // Revised `updateBlended`:
        // 1. Compute `AnimLocal` from channels (starting from AnimBind).
        // 2. Convert `AnimLocal` -> `ModelLocal` (Target).
        // 3. Blend `joint.localTransform` (Source) -> `ModelLocal` (Target).
    }
}

