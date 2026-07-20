package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.flag.ImGuiDragDropFlags

fun Animator.imgui(stringManager: StringManager, logger: LoggerService) {
    val renderComponent = gameObject.getComponent<RenderComponent>()
    val skeletonComponent = gameObject.getComponent<SkeletonComponent>()
    val model = renderComponent?.model

    if (model == null) {
        ImGui.text(stringManager.getString("lbl.animator.no_render_comp"))
        return
    }

    ImGui.beginGroup()
    if (ImGui.beginCombo(
            stringManager.getString("lbl.animator.animations"),
            currentAnimation?.name
                ?: if (animations.isEmpty()) stringManager.getString("lbl.animator.drop_animations") else stringManager.getString(
                    "lbl.animator.select"
                )
        )
    ) {
        for (anim in animations) {
            if (ImGui.selectable("${anim.name} (${String.format("%.2f", anim.duration)}s)", currentAnimation == anim)) {
                play(anim)
            }
        }
        ImGui.endCombo()
    }
    ImGui.endGroup()

    if (ImGui.beginDragDropTarget()) {
        val newAnimation = ImGui.acceptDragDropPayload<Animation>("ANIMATION", ImGuiDragDropFlags.None)
        if (newAnimation != null) {

            val skeleton = gameObject.getComponent<SkeletonComponent>()?.pose?.skeleton
            val isValid = validateSkeletonCompatibility(skeleton, newAnimation)
            val alreadyHasAnimation = animations.any { it.name == newAnimation.name }

            when {
                skeleton == null -> logger.log(
                    "Animation '${newAnimation.name}' cannot be added because ${gameObject.name} does not currently have a skeleton.",
                    LogLevel.ERROR
                )

                !isValid -> logger.log(
                    "Animation '${newAnimation.name}' is incompatible with the current skeleton. Skipping.",
                    LogLevel.ERROR
                )

                alreadyHasAnimation -> logger.log(
                    "'${gameObject.name}' already contains '${newAnimation.name}' animation.",
                    LogLevel.ERROR
                )

                else -> {
                    if (addAnimation(newAnimation)) {
                        logger.log("Animation '${newAnimation.name}' added successfully!", LogLevel.ACTION)
                    }
                }
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