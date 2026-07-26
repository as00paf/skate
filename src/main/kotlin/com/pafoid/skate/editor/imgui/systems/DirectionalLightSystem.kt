package com.pafoid.skate.editor.imgui.systems

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import imgui.ImGui

fun DirectionalLightSystem.imgui(stringManager: StringManager) {
    val config = config ?: return

    ImGui.separator()
    ImGui.text(stringManager.getString("lbl.directional_light.shadow_distance"))

    val shadowDistanceArr = floatArrayOf(config.shadowDistance)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.directional_light.shadow_distance_m"),
            shadowDistanceArr,
            0.1f,
            10f,
            200f
        )
    ) {
        config.shadowDistance = shadowDistanceArr[0]
    }

    val autoCalcBounds = config.autoCalculateBounds
    if (ImGui.checkbox(stringManager.getString("lbl.directional_light.auto_calculate_bounds"), autoCalcBounds)) {
        config.autoCalculateBounds = !autoCalcBounds
    }

    if (!config.autoCalculateBounds) {
        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.directional_light.orthographic_bounds"))

        val orthoLeft = floatArrayOf(config.orthoLeft)
        if (ImGui.dragFloat(stringManager.getString("lbl.directional_light.left"), orthoLeft, 0.1f, -100f, 0f)) {
            config.orthoLeft = orthoLeft[0]
        }

        val orthoRight = floatArrayOf(config.orthoRight)
        if (ImGui.dragFloat(stringManager.getString("lbl.directional_light.right"), orthoRight, 0.1f, 0f, 100f)) {
            config.orthoRight = orthoRight[0]
        }

        val orthoBottom = floatArrayOf(config.orthoBottom)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.directional_light.bottom"),
                orthoBottom,
                0.1f,
                -100f,
                0f
            )
        ) {
            config.orthoBottom = orthoBottom[0]
        }

        val orthoTop = floatArrayOf(config.orthoTop)
        if (ImGui.dragFloat(stringManager.getString("lbl.directional_light.top"), orthoTop, 0.1f, 0f, 100f)) {
            config.orthoTop = orthoTop[0]
        }
    }

    ImGui.separator()
    ImGui.text(stringManager.getString("lbl.directional_light.shadow_quality"))

    val stabilizeProj = config.stabilizeProjection
    if (ImGui.checkbox(stringManager.getString("lbl.directional_light.stabilize_projection"), stabilizeProj)) {
        config.stabilizeProjection = !stabilizeProj
    }

    val depthBiasArr = floatArrayOf(config.depthBias)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.directional_light.depth_bias"),
            depthBiasArr,
            0.0001f,
            0.0f,
            0.1f,
            "%.4f"
        )
    ) {
        config.depthBias = depthBiasArr[0]
    }

    val slopeBiasArr = floatArrayOf(config.slopeScaledBias)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.directional_light.slope_scaled_bias"),
            slopeBiasArr,
            0.001f,
            0.0f,
            0.1f,
            "%.3f"
        )
    ) {
        config.slopeScaledBias = slopeBiasArr[0]
    }
}