package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import imgui.ImGui
import imgui.flag.ImGuiDragDropFlags
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

/**
 * Component responsible for managing and playing skeletal animations.
 * 
 * It handles:
 * - Playback state (isPlaying, currentTime).
 * - Cross-fading between animations (Blending).
 * - Applying bone overrides from other components.
 * - Skeleton hierarchy updates.
 * - Editor visualization of the bone hierarchy.
 */
class Animator : Component(), KoinComponent {
    private val debugDraw: DebugDraw by inject()
    private val resourceManager: ResourceManager by inject()

    // Reusable objects to minimize allocations in hot loops/recursive calls
    private val tempJointPos = Vector3f()
    private val tempChildPos = Vector3f()
    private val tempJointQuat = Quaternionf()
    private val boneColor = Vector3f(0f, 1f, 1f) // Cyan for bones

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

    /**
     * Starts playing a new animation with an optional [blend] time for smooth transitions.
     * 
     * @param animation The animation to play.
     * @param blend Transition duration in seconds.
     */
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
        val skeleton = entity.model.skeleton ?: return
        
        if (isPlaying) {
            val animation = currentAnimation ?: entity.model.animations.firstOrNull() ?: return
            
            // Only log every 60 frames to avoid spam
            if (System.currentTimeMillis() % 1000 < 20) {
                val matched = animation.channels.count { skeleton.getJointByName(it.targetNodeName) != null }
                println("Animator: Playing '${animation.name}', matched $matched/${animation.channels.size} channels")
            }

            currentTime += dt
            
            if (blendTime > 0f) {
                blendTime -= dt
                val alpha = 1f - (blendTime / blendDuration)
                previousAnimation?.let { prev ->
                    previousTime += dt
                    prev.update(previousTime, skeleton)
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
        joint.worldTransform.getTranslation(tempJointPos)
        modelMatrix.transformPosition(tempJointPos)
        
        // Capture joint position for this recursion level
        val currentJointPos = Vector3f(tempJointPos)
        
        for (child in joint.children) {
            child.worldTransform.getTranslation(tempChildPos)
            modelMatrix.transformPosition(tempChildPos)
            
            debugDraw.addLine3D(currentJointPos, tempChildPos, boneColor)
            visualizeJoint(child, modelMatrix)
        }
        
        // Draw joint point as a tiny box
        joint.worldTransform.getUnnormalizedRotation(tempJointQuat)
        debugDraw.addBox3D(currentJointPos, tempJointQuat, Vector3f(0.01f), boneColor)
    }

    fun stop() {
        isPlaying = false
    }

    fun resume() {
        isPlaying = true
    }

    override fun imgui() {
        val entity = gameObject.getComponent<Entity>()
        val animations = entity?.model?.animations.orEmpty()
        if (entity == null) {
            ImGui.text("No entity found")
            return
        }

        ImGui.beginGroup()
        if (ImGui.beginCombo("Animations", currentAnimation?.name ?: if (animations.isEmpty()) "Drop animations here..." else "Select...")) {
            for (anim in animations) {
                if (ImGui.selectable("${anim.name} (${String.format("%.2f", anim.duration)}s)", currentAnimation == anim)) {
                    play(anim)
                }
            }
            ImGui.endCombo()
        }
        ImGui.endGroup()

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ANIMATION", ImGuiDragDropFlags.None)
            if (payload != null) {
                val path = payload
                val newAnims = resourceManager.loadAnimationsSync(path)
                entity.model.addAnimations(newAnims)
            }
            ImGui.endDragDropTarget()
        }

        if (animations.isEmpty()) return

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