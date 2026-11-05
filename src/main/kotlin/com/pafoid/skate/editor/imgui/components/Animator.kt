package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.animation.AnimatorControllerCatalog
import com.pafoid.skate.editor.events.AnimatorAction
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.engine.animation.graph.MaskPreset
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.flag.ImGuiDragDropFlags
import java.io.File

fun Animator.imgui(engine: Engine) {
    val renderComponent = gameObject?.getComponent<RenderComponent>()
    val skeletonComponent = gameObject?.getComponent<SkeletonComponent>()
    val model = renderComponent?.model

    if (model == null) {
        ImGui.text(engine.stringManager.getString("lbl.animator.no_render_comp"))
        return
    }

    controllerSection(engine)
    maskSection(engine, skeletonComponent)
    ImGui.separator()

    ImGui.beginGroup()
    if (ImGui.beginCombo(
            engine.stringManager.label("lbl.animator.animations"),
            currentAnimation?.name
                ?: if (animations.isEmpty()) engine.stringManager.getString("lbl.animator.drop_animations") else engine.stringManager.getString(
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

            val skeleton = gameObject?.getComponent<SkeletonComponent>()?.pose?.skeleton
            val isValid = validateSkeletonCompatibility(skeleton, newAnimation)
            val alreadyHasAnimation = animations.any { it.name == newAnimation.name }

            when {
                skeleton == null -> engine.logger.log(
                    "Animation '${newAnimation.name}' cannot be added because ${gameObject?.name} does not currently have a skeleton.",
                    LogLevel.ERROR
                )

                !isValid -> engine.logger.log(
                    "Animation '${newAnimation.name}' is incompatible with the current skeleton. Skipping.",
                    LogLevel.ERROR
                )

                alreadyHasAnimation -> engine.logger.log(
                    "'${gameObject?.name}' already contains '${newAnimation.name}' animation.",
                    LogLevel.ERROR
                )

                else -> {
                    if (addAnimation(newAnimation)) {
                        engine.logger.log("Animation '${newAnimation.name}' added successfully!", LogLevel.ACTION)
                    }
                }
            }
        }
        ImGui.endDragDropTarget()
    }

    if (animations.isEmpty()) return
    val anim = currentAnimation ?: return

    ImGui.text(engine.stringManager.getString("lbl.animator.duration", anim.duration))
    val timeArr = floatArrayOf(currentTime)
    if (ImGui.sliderFloat(engine.stringManager.getString("lbl.animator.timeline"), timeArr, 0f, anim.duration)) {
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

    if (ImGui.button(
            if (isPlaying) engine.stringManager.getString("lbl.animator.pause") else engine.stringManager.getString(
                "lbl.animator.play"
            )
        )
    ) {
        isPlaying = !isPlaying
    }
    ImGui.sameLine()
    if (ImGui.button(engine.stringManager.getString("btn.reset"))) {
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
/** Controller asset picker (bundled + project `*.controller.json`, or drop one from the Project window), EDT-16. */
private fun Animator.controllerSection(engine: Engine) {
    val strings = engine.stringManager
    val catalog = AnimatorControllerCatalog.shared
    val none = strings.getString("lbl.animator.controller_none")
    val preview = controllerPath?.let { AnimatorControllerCatalog.displayName(it) } ?: none
    if (ImGui.beginCombo(strings.label("lbl.animator.controller"), preview)) {
        if (ImGui.selectable("$none###controller_none", controllerPath == null)) {
            engine.eventSystem.publish(AnimatorAction.SetController(this, null))
        }
        for (path in catalog.options(controllerPath)) {
            if (ImGui.selectable("${AnimatorControllerCatalog.displayName(path)}###controller:$path", path == controllerPath)) {
                engine.eventSystem.publish(AnimatorAction.SetController(this, path))
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip(path)
        }
        ImGui.endCombo()
    }
    if (ImGui.beginDragDropTarget()) {
        val dropped = ImGui.acceptDragDropPayload<String>("FILE_PATH", ImGuiDragDropFlags.None)
        if (dropped != null && AnimatorControllerCatalog.isController(dropped)) {
            engine.eventSystem.publish(AnimatorAction.SetController(this, File(dropped).absolutePath))
        }
        ImGui.endDragDropTarget()
    }
    ImGui.sameLine()
    if (ImGui.button("${strings.getString("lbl.animator.controller_refresh")}##controller_refresh")) catalog.refresh()
    if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("lbl.animator.controller_drop_hint"))

    val machine = stateMachine
    when {
        machine != null -> {
            ImGui.text(strings.getString("lbl.animator.state", AnimatorControllerCatalog.stateReadout(machine)))
            ImGui.progressBar(machine.normalizedTime, 0f, 0f, "")
            requestedState?.let { ImGui.text(strings.getString("lbl.animator.requested", it)) }
        }
        controllerPath != null -> ImGui.textDisabled(strings.getString("lbl.animator.controller_inactive"))
    }
}

/** Bone mask picker: preset, and the subtree root bone for [MaskPreset.SUBTREE]. */
private fun Animator.maskSection(engine: Engine, skeletonComponent: SkeletonComponent?) {
    val strings = engine.stringManager
    fun presetName(p: MaskPreset) = strings.getString("lbl.animator.mask.${p.name.lowercase()}")
    if (ImGui.beginCombo(strings.label("lbl.animator.mask"), presetName(mask.preset))) {
        for (preset in MaskPreset.entries) {
            if (ImGui.selectable("${presetName(preset)}###mask:${preset.name}", preset == mask.preset)) {
                engine.eventSystem.publish(AnimatorAction.SetMask(this, mask.copy(preset = preset)))
            }
        }
        ImGui.endCombo()
    }
    if (mask.preset != MaskPreset.SUBTREE) return
    val bones = skeletonComponent?.pose?.skeleton?.getAllBones()?.map { it.name } ?: return
    val preview = mask.rootBone.ifBlank { strings.getString("lbl.animator.select") }
    if (ImGui.beginCombo(strings.label("lbl.animator.mask_bone"), preview)) {
        for (bone in bones) {
            if (ImGui.selectable("$bone###mask_bone:$bone", bone == mask.rootBone)) {
                engine.eventSystem.publish(AnimatorAction.SetMask(this, mask.copy(rootBone = bone)))
            }
        }
        ImGui.endCombo()
    }
}
