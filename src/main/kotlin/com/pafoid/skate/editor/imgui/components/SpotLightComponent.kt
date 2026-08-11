package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import imgui.ImGui

fun SpotLightComponent.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    // Light color
    val lightColor = floatArrayOf(color.x, color.y, color.z)
    if (MImGui.colorEdit3(stringManager.getString("component.SpotLightComponent.color"), lightColor)) {
        color.set(lightColor[0], lightColor[1], lightColor[2])//TODO: use event system
    }

    // Light intensity
    val intensityArr = floatArrayOf(intensity)
    ImGui.text(stringManager.getString("component.SpotLightComponent.intensity"))
    ImGui.sameLine()
    ImGui.pushItemWidth(ImGui.getContentRegionAvailX())
    if (ImGui.sliderFloat(
            "##light_intensity",
            intensityArr,
            0.0f,
            2.0f
        )
    ) {
        intensity = intensityArr[0].coerceIn(0.0f, 2.0f)
    }
    ImGui.popItemWidth()

    constant = MImGui.dragFloat(stringManager.getString("component.SpotLightComponent.constant"), constant)
    linear = MImGui.dragFloat(stringManager.getString("component.SpotLightComponent.linear"), linear)
    quadratic = MImGui.dragFloat(stringManager.getString("component.SpotLightComponent.quadratic"), quadratic)
    cutOff = MImGui.dragFloat(stringManager.getString("component.SpotLightComponent.cutOff"), cutOff)
    outerCutOff = MImGui.dragFloat(stringManager.getString("component.SpotLightComponent.outerCutOff"), outerCutOff)

}