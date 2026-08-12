package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.SpotLightComponent

fun SpotLightComponent.imgui(engine: Engine) {
    // Light color
    val lightColor = floatArrayOf(color.x, color.y, color.z)
    if (MImGui.colorEdit3(engine.stringManager.getString("component.SpotLightComponent.color"), lightColor)) {
        color.set(lightColor[0], lightColor[1], lightColor[2])//TODO: use event system
    }

    // Light intensity
    intensity = MImGui.sliderFloat(intensity, engine.stringManager.getString("component.SpotLightComponent.intensity"))

    // Direction
    MImGui.drawVec3Control(engine.stringManager.getString("component.SpotLightComponent.direction"), direction)

    constant = MImGui.sliderFloat(constant, engine.stringManager.getString("component.SpotLightComponent.constant"))
    linear = MImGui.sliderFloat(linear, engine.stringManager.getString("component.SpotLightComponent.linear"))
    quadratic = MImGui.sliderFloat(quadratic, engine.stringManager.getString("component.SpotLightComponent.quadratic"))
    cutOff = MImGui.dragFloat(engine.stringManager.getString("component.SpotLightComponent.cutOff"), cutOff)
    outerCutOff =
        MImGui.dragFloat(engine.stringManager.getString("component.SpotLightComponent.outerCutOff"), outerCutOff)

}