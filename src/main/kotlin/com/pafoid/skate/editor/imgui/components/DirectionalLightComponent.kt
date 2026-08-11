package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import imgui.ImGui

fun DirectionalLightComponent.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    // Light color
    val lightColor = floatArrayOf(color.x, color.y, color.z)
    if (MImGui.colorEdit3(stringManager.getString("component.DirectionalLightComponent.color"), lightColor)) {
        color.set(lightColor[0], lightColor[1], lightColor[2])//TODO: use event system
    }

    // Light intensity
    intensity = MImGui.sliderFloat(intensity, stringManager.getString("component.DirectionalLightComponent.intensity"))

    // Direction
    MImGui.drawVec3Control(stringManager.getString("component.DirectionalLightComponent.direction"), direction)

    orthoLeft = MImGui.dragFloat(stringManager.getString("component.DirectionalLightComponent.orthoLeft"), orthoLeft)
    orthoRight = MImGui.dragFloat(stringManager.getString("component.DirectionalLightComponent.orthoRight"), orthoRight)
    orthoBottom =
        MImGui.dragFloat(stringManager.getString("component.DirectionalLightComponent.orthoBottom"), orthoBottom)
    orthoTop = MImGui.dragFloat(stringManager.getString("component.DirectionalLightComponent.orthoTop"), orthoTop)
    orthoNear = MImGui.dragFloat(stringManager.getString("component.DirectionalLightComponent.orthoNear"), orthoNear)
    orthoFar = MImGui.dragFloat(stringManager.getString("component.DirectionalLightComponent.orthoFar"), orthoFar)
    depthBias = MImGui.dragFloat(stringManager.getString("component.DirectionalLightComponent.depthBias"), depthBias)
    slopeScaledBias = MImGui.dragFloat(
        stringManager.getString("component.DirectionalLightComponent.slopeScaledBias"),
        slopeScaledBias
    )
    shadowDistance =
        MImGui.dragFloat(stringManager.getString("component.DirectionalLightComponent.shadowDistance"), shadowDistance)

    if (ImGui.checkbox(stringManager.getString("component.DirectionalLightComponent.castShadows"), castShadows)) {
        castShadows = !castShadows
    }
    if (ImGui.checkbox(
            stringManager.getString("component.DirectionalLightComponent.stabilizeProjection"),
            stabilizeProjection
        )
    ) {
        stabilizeProjection = !stabilizeProjection
    }
    if (ImGui.checkbox(
            stringManager.getString("component.DirectionalLightComponent.autoCalculateBounds"),
            autoCalculateBounds
        )
    ) {
        autoCalculateBounds = !autoCalculateBounds
    }
}