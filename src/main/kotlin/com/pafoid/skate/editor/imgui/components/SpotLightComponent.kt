package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.SpotLightComponent

fun SpotLightComponent.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    // Light color
    val lightColor = floatArrayOf(color.x, color.y, color.z)
    if (MImGui.colorEdit3(stringManager.getString("component.SpotLightComponent.color"), lightColor)) {
        color.set(lightColor[0], lightColor[1], lightColor[2])//TODO: use event system
    }

    // Light intensity
    intensity = MImGui.sliderFloat(intensity, stringManager.getString("component.SpotLightComponent.intensity"))

    // Direction
    MImGui.drawVec3Control(stringManager.getString("component.SpotLightComponent.direction"), direction)

    constant = MImGui.sliderFloat(constant, stringManager.getString("component.SpotLightComponent.constant"))
    linear = MImGui.sliderFloat(linear, stringManager.getString("component.SpotLightComponent.linear"))
    quadratic = MImGui.sliderFloat(quadratic, stringManager.getString("component.SpotLightComponent.quadratic"))
    cutOff = MImGui.dragFloat(stringManager.getString("component.SpotLightComponent.cutOff"), cutOff)
    outerCutOff = MImGui.dragFloat(stringManager.getString("component.SpotLightComponent.outerCutOff"), outerCutOff)

}