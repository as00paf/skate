package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import imgui.ImGui
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

class Animator : Component() {
    var currentTime = 0f
        private set
    var currentAnimation: Animation? = null
        private set
    var isPlaying = true
    private var blendTime = 0f
    private var blendDuration = 0.2f
    private var previousAnimation: Animation? = null
    private var previousTime = 0f

    val normalizedTime: Float
        get() {
            val anim = currentAnimation ?: return 0f
            return (currentTime % anim.duration) / anim.duration
        }

    val duration: Float
        get() = currentAnimation?.duration ?: 0f

    fun play(animation: Animation, blend: Float = 0.2f) {
        if (currentAnimation == animation) return
        
        previousAnimation = currentAnimation
        previousTime = currentTime
        blendTime = blend
        blendDuration = blend
        
        currentAnimation = animation
        currentTime = 0f
        isPlaying = true
    }

    fun play(name: String, blend: Float = 0.2f) {
        val entity = gameObject.getComponent<Entity>() ?: return
        val anim = entity.model.animations.find { it.name.contains(name, ignoreCase = true) } ?: return
        play(anim, blend)
    }

    override fun update(dt: Float) {
        val entity = gameObject.getComponent<Entity>() ?: return
        val skeleton = entity.gameObject.getComponent<Skeleton>() ?: entity.model.skeleton ?: return
        
        if (isPlaying) {
            val animation = currentAnimation ?: entity.model.animations.firstOrNull() ?: return

            currentTime += dt
            
            if (blendTime > 0f) {
                blendTime -= dt
                val alpha = 1f - (blendTime / blendDuration)
                previousAnimation?.let { prev ->
                    prev.update(previousTime, skeleton)
                    previousTime += dt
                    animation.updateBlended(currentTime, skeleton, alpha)
                } ?: animation.update(currentTime, skeleton)
            } else {
                animation.update(currentTime, skeleton)
            }
        }
        
        // Apply bone overrides if they exist
        gameObject.getComponent<BoneOverride>()?.let { overrideComponent ->
            skeleton.getAllJoints().forEach { joint ->
                overrideComponent.getOverride(joint.name)?.let { overrideRotation ->
                    // Decompose matrix
                    val translation = Vector3f()
                    val rotation = Quaternionf()
                    val scale = Vector3f()
                    joint.localTransform.getTranslation(translation)
                    joint.localTransform.getUnnormalizedRotation(rotation)
                    joint.localTransform.getScale(scale)
                    
                    // Apply override by multiplying rotations
                    rotation.mul(overrideRotation)
                    
                    // Recompose matrix
                    joint.localTransform.translationRotateScale(translation, rotation, scale)
                }
            }
        }
        
        // Always update skeleton matrices even if animation is paused
        // This allows procedural logic in other components to take effect
        skeleton.update()
    }

    override fun editorUpdate(dt: Float) {
        val entity = gameObject.getComponent<Entity>() ?: return
        val skeleton = entity.model.skeleton ?: return
        
        // Visualize bones in editor mode
        visualizeJoint(skeleton.rootJoint, gameObject.transform.toWorldMatrix())
        
        if (isPlaying) {
            update(dt)
        }
    }

    private fun visualizeJoint(joint: Joint, modelMatrix: Matrix4f) {
        val jointPos = Vector3f()
        joint.worldTransform.getTranslation(jointPos)
        modelMatrix.transformPosition(jointPos)
        
        val color = Vector3f(0f, 1f, 1f) // Cyan for bones
        
        for (child in joint.children) {
            val childPos = Vector3f()
            child.worldTransform.getTranslation(childPos)
            modelMatrix.transformPosition(childPos)
            
            com.pafoid.skate.engine.render.DebugDraw.addLine3D(jointPos, childPos, color)
            visualizeJoint(child, modelMatrix)
        }
        
        // Draw joint point as a tiny box
        val quat = Quaternionf()
        joint.worldTransform.getUnnormalizedRotation(quat)
        com.pafoid.skate.engine.render.DebugDraw.addBox3D(jointPos, quat, Vector3f(0.01f), color)
    }

    fun stop() {
        isPlaying = false
    }

    fun resume() {
        isPlaying = true
    }

    override fun imgui() {
        val entity = gameObject.getComponent<Entity>() ?: return
        val animations = entity.model.animations
        if (animations.isEmpty()) {
            ImGui.text("No animations found in model")
            return
        }

        if (ImGui.beginCombo("Animations", currentAnimation?.name ?: "Select...")) {
            for (anim in animations) {
                if (ImGui.selectable("${anim.name} (${String.format("%.2f", anim.duration)}s)", currentAnimation == anim)) {
                    play(anim)
                }
            }
            ImGui.endCombo()
        }

        val anim = currentAnimation ?: animations.firstOrNull() ?: return
        
        ImGui.text("Duration: ${String.format("%.2f", anim.duration)}s")
        val timeArr = floatArrayOf(currentTime)
        if (ImGui.sliderFloat("Timeline", timeArr, 0f, anim.duration)) {
            currentTime = timeArr[0]
            isPlaying = false // Scrubbing pauses playback for precision
            
            // Force update skeleton when scrubbing
            entity.model.skeleton?.let { anim.update(currentTime, it) }
        }

        if (ImGui.button(if (isPlaying) "Pause" else "Play")) {
            isPlaying = !isPlaying
        }
        ImGui.sameLine()
        if (ImGui.button("Reset")) {
            currentTime = 0f
            entity.model.skeleton?.let { anim.update(currentTime, it) }
        }
    }
}