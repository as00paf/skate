package com.pafoid.skate.editor.imgui.systems

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.getComponent
import imgui.ImGui

var globalSpeedMultiplier: Float = 1.0f

fun AnimationSystem.imgui(stringManager: StringManager) {
    ImGui.text(stringManager.getString("lbl.animation_system.animated_objects", cache.size))
    ImGui.text(stringManager.getString("lbl.animation_system.cache_dirty", cacheDirty))

    ImGui.separator()

    // Global speed multiplier
    val speedArr = floatArrayOf(globalSpeedMultiplier)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.animation_system.global_speed_multiplier"),
            speedArr,
            0.1f,
            0f,
            3f,
            "%.2f"
        )
    ) {
        globalSpeedMultiplier = speedArr[0].coerceIn(0f, 3f)
    }

    ImGui.separator()
    ImGui.text(stringManager.getString("lbl.animation_system.per_object_state"))

    // Show each animated object's state
    cache.forEach { go ->
        val animator = go.getComponent<Animator>()
        val skeletonComponent = go.getComponent<SkeletonComponent>()

        if (animator != null && skeletonComponent != null) {
            val goName = go.name
            val currentAnim = animator.currentAnimation
            val isPlaying = animator.isPlaying
            val currentTime = animator.currentTime
            val duration = currentAnim?.duration ?: 0f
            val blendTime = animator.blendTime

            ImGui.text("$goName:")
            ImGui.indent()

            if (currentAnim != null) {
                ImGui.text(stringManager.getString("lbl.animation_system.animation", currentAnim.name))
                ImGui.text(stringManager.getString("lbl.animation_system.time", currentTime, duration))
                ImGui.text(stringManager.getString("lbl.animation_system.playing", isPlaying))

                if (blendTime > 0f) {
                    ImGui.text(stringManager.getString("lbl.animation_system.blending", blendTime))
                }
            } else {
                ImGui.text(stringManager.getString("lbl.animation_system.no_animation"))
            }

            ImGui.unindent()
        }
    }

    if (cache.isEmpty()) {
        ImGui.text(stringManager.getString("lbl.animation_system.no_objects"))
    }
}