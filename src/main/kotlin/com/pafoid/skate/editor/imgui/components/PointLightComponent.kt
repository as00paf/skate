package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.PointLightComponent

fun PointLightComponent.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    // Light color
    val lightColor = floatArrayOf(color.x, color.y, color.z)
    if (MImGui.colorEdit3(stringManager.getString("component.PointLightComponent.color"), lightColor)) {
        color.set(lightColor[0], lightColor[1], lightColor[2])//TODO: use event system
    }

    // Light intensity
    intensity = MImGui.sliderFloat(intensity, stringManager.getString("component.PointLightComponent.intensity"))

    constant = MImGui.sliderFloat(constant, stringManager.getString("component.PointLightComponent.constant"))
    linear = MImGui.sliderFloat(linear, stringManager.getString("component.PointLightComponent.linear"))
    quadratic = MImGui.sliderFloat(quadratic, stringManager.getString("component.PointLightComponent.quadratic"))

}