package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.utils.BoneNameMapper
import com.pafoid.skate.engine.utils.StringManager
import imgui.ImGui
import imgui.flag.ImGuiDragDropFlags
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
    private val resourceManager: ResourceManager by inject()
    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()

    var currentAnimation: Animation? = null
        get() {
            if (field == null) return animations.firstOrNull()
            return field
        }
        private set

    var currentTime = 0f
    var isPlaying = false
    var blendTime = 0f
    var blendDuration = 0.2f
    var previousAnimation: Animation? = null
    var previousTime = 0f


    private val animations: MutableList<Animation> = mutableListOf()

    fun addAnimation(animation: Animation) {
        val skeleton = gameObject.getComponent<SkeletonComponent>()?.pose?.skeleton
        val isValid = validateSkeletonCompatibility(skeleton, animation)
        val alreadyHasAnimation = animations.any { it.name == animation.name }

        when {
            skeleton == null -> logger.logEngine(
                "Animation '${animation.name}' cannot be added because ${gameObject.name} does not currently have a skeleton.",
                LogLevel.ERROR
            )

            !isValid -> logger.logEngine(
                "Animation '${animation.name}' is incompatible with the current skeleton. Skipping.",
                LogLevel.ERROR
            )

            alreadyHasAnimation -> logger.logEngine(
                "'${gameObject.name}' already contains '${animation.name}' animation.",
                LogLevel.ERROR
            )

            else -> {
                animations.add(animation)
                logger.logEngine("Animation '${animation.name}' added successfully!", LogLevel.ACTION)
            }
        }
    }

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
        val anim = animations.find { it.name.contains(name, ignoreCase = true) } ?: return
        play(anim, blend)
    }

    override fun update(dt: Float) {}

    override fun imgui() {
        val renderComponent = gameObject.getComponent<RenderComponent>()
        val skeletonComponent = gameObject.getComponent<SkeletonComponent>()
        val model = renderComponent?.model
        
        if (model == null) {
            ImGui.text(stringManager.getString("lbl.animator.no_render_comp"))
            return
        }

        ImGui.beginGroup()
        if (ImGui.beginCombo(stringManager.getString("lbl.animator.animations"), currentAnimation?.name ?: if (animations.isEmpty()) stringManager.getString("lbl.animator.drop_animations") else stringManager.getString("lbl.animator.select"))) {
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
                val newAnim = resourceManager.getAnimation(path)
                newAnim?.let {
                    addAnimation(it)
                }
            }
            ImGui.endDragDropTarget()
        }

        if (animations.isEmpty()) return
        val anim = currentAnimation ?: return

        ImGui.text(stringManager.getString("lbl.animator.duration", anim.duration))
        val timeArr = floatArrayOf(currentTime)
        if (ImGui.sliderFloat(stringManager.getString("lbl.animator.timeline"), timeArr, 0f, anim.duration)) {
            currentTime = timeArr[0]
            isPlaying = false // Scrubbing pauses playback for precision

            // Force update skeleton when scrubbing
            skeletonComponent?.pose?.let { pose ->
                val skeleton = pose.skeleton
                anim.update(currentTime, skeleton)
                // Copy to pose so AnimationSystem picks it up
                skeleton.getAllBones().forEach { bone ->
                    if (bone.index in 0 until pose.localTransforms.size) {
                        pose.localTransforms[bone.index].set(bone.localTransform)
                    }
                }
            }
        }

        if (ImGui.button(if (isPlaying) stringManager.getString("lbl.animator.pause") else stringManager.getString("lbl.animator.play"))) {
            isPlaying = !isPlaying
        }
        ImGui.sameLine()
        if (ImGui.button(stringManager.getString("btn.reset"))) {
            currentTime = 0f
            skeletonComponent?.pose?.let { pose ->
                val skeleton = pose.skeleton
                anim.update(currentTime, skeleton)
                // Copy to pose so AnimationSystem picks it up
                skeleton.getAllBones().forEach { bone ->
                    if (bone.index in 0 until pose.localTransforms.size) {
                        pose.localTransforms[bone.index].set(bone.localTransform)
                    }
                }
            }
        }
    }

    fun validateSkeletonCompatibility(skeleton: Skeleton?, animation: Animation): Boolean {
        skeleton ?: return false
        // Check if all animation channels target bones that exist in the skeleton
        val missingBones = mutableListOf<String>()
        for (channel in animation.channels) {
            val boneName = BoneNameMapper.map(channel.targetNodeName)
            val bone = skeleton.getBoneByName(boneName)
            if (bone == null) {
                missingBones.add(boneName)
            }
        }
        
        if (missingBones.isNotEmpty()) {
            // Check if the missing bones are critical or just extra detail bones
            val criticalBones = missingBones.filter { isCriticalBone(it) }
            val optionalBones = missingBones.filter { !isCriticalBone(it) }
            
            if (criticalBones.isNotEmpty()) {
                logger.logEngine("Animation '${animation.name}' targets critical bones that do not exist in the skeleton: ${criticalBones.joinToString(", ")}.", LogLevel.ERROR)
                return false
            } else if (optionalBones.isNotEmpty()) {
                logger.logEngine("Animation '${animation.name}' targets optional bones that do not exist in the skeleton: ${optionalBones.joinToString(", ")}. Animation may have reduced fidelity.", LogLevel.WARN)
                // Still return true as these are optional bones
            }
        }
        
        // Check if the skeleton has significantly more bones than the animation targets
        val skeletonBoneNames = skeleton.getAllBones().map { it.name }.toSet()
        val animationBoneNames = animation.channels.map { BoneNameMapper.map(it.targetNodeName) }.toSet()
        val extraSkeletonBones = skeletonBoneNames - animationBoneNames
        
        // Only warn if there are many extra bones (indicating a major topology mismatch)
        if (extraSkeletonBones.size > skeletonBoneNames.size * 0.5) { // More than 50% of bones unused
            logger.logEngine("Skeleton has many bones not targeted by animation '${animation.name}'. This may indicate a topology mismatch.", LogLevel.WARN)
        }
        
        return true
    }
    
    private fun isCriticalBone(boneName: String): Boolean {
        // Define critical bones that are essential for basic animation
        val criticalBonePatterns = listOf(
            "hip", "hips", "pelvis", "spine", "chest", "neck", "head",
            "leftupperarm", "rightupperarm", "leftlowerarm", "rightlowerarm", 
            "lefthand", "righthand", "leftthigh", "rightthigh", 
            "leftcalf", "rightcalf", "leftfoot", "rightfoot"
        )
        
        val lowerName = boneName.lowercase()
        return criticalBonePatterns.any { lowerName.contains(it) }
    }
}