package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import imgui.ImGui

fun RenderComponent.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    textureScale = MImGui.dragFloat(
        stringManager.getString("component.RenderComponent.textureScale"),
        textureScale
    )

    renderMode = MImGui.enumCheckbox(stringManager.getString("component.RenderComponent.renderMode"), renderMode)

    val castShadowLabel = stringManager.getString("component.RenderComponent.castShadow")
    if (ImGui.checkbox(castShadowLabel, castShadow)) {
        castShadow = !castShadow
    }

    val receiveShadowLabel = stringManager.getString("component.RenderComponent.receiveShadow")
    if (ImGui.checkbox(receiveShadowLabel, receiveShadow)) {
        receiveShadow = !receiveShadow
    }
}