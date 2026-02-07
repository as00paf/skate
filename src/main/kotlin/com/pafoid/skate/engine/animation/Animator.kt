package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.utils.BoneNameMapper
import imgui.ImGui
import imgui.flag.ImGuiDragDropFlags
import kotlinx.serialization.Contextual
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Collections

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

    var currentTime = 0f
    var currentAnimation: Animation? = null
        private set
    var isPlaying = false
    var blendTime = 0f
    var blendDuration = 0.2f
    var previousAnimation: Animation? = null
    var previousTime = 0f


    val animations: MutableList<Animation> = mutableListOf()

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
            ImGui.text("No render component found")
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
                val newAnimations = resourceManager.loadAnimationsSync(path)

                newAnimations.forEach { newAnim ->
                    if (animations.none { it.name == newAnim.name }) {
                        // Check skeleton compatibility before adding the animation
                        val skeletonComponent = gameObject.getComponent<SkeletonComponent>()
                        if (skeletonComponent?.pose?.skeletonAsset != null) {
                            if (validateSkeletonCompatibility(skeletonComponent.pose.skeletonAsset, newAnim)) {
                                animations.add(newAnim)
                            } else {
                                // Log the incompatibility
                                logger.logEngine("Animation '${newAnim.name}' is incompatible with the current skeleton. Skipping.", LogLevel.ERROR)
                            }
                        } else {
                            animations.add(newAnim)
                        }
                    }
                }
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
            skeletonComponent?.pose?.let { pose ->
                val skeleton = pose.skeletonAsset
                anim.update(currentTime, skeleton)
                // Copy to pose so AnimationSystem picks it up
                skeleton.getAllBones().forEach { bone ->
                    if (bone.index in 0 until pose.localTransforms.size) {
                        pose.localTransforms[bone.index].set(bone.localTransform)
                    }
                }
            }
        }

        if (ImGui.button(if (isPlaying) "Pause" else "Play")) {
            isPlaying = !isPlaying
        }
        ImGui.sameLine()
        if (ImGui.button("Reset")) {
            currentTime = 0f
            skeletonComponent?.pose?.let { pose ->
                val skeleton = pose.skeletonAsset
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

    fun validateSkeletonCompatibility(skeleton: Skeleton, animation: Animation): Boolean {
        // Check if all animation channels target bones that exist in the skeleton
        for (channel in animation.channels) {
            val boneName = BoneNameMapper.map(channel.targetNodeName)
            val bone = skeleton.getBoneByName(boneName)
            if (bone == null) {
                logger.logEngine("Animation '${animation.name}' targets bone '$boneName' which does not exist in the skeleton.", LogLevel.ERROR)
                return false
            }
        }
        return true
    }
}