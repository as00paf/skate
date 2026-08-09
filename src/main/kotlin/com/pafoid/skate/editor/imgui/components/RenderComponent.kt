package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import imgui.ImGui

// TODO: use event system/commands to support undo/redo
fun RenderComponent.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    textureScale = MImGui.dragFloat(
        stringManager.getString("component.RenderComponent.textureScale"),
        textureScale
    )

    renderMode = MImGui.enumDropdown(stringManager.getString("component.RenderComponent.renderMode"), renderMode)

    val castShadowLabel = stringManager.getString("component.RenderComponent.castShadow")
    if (ImGui.checkbox(castShadowLabel, castShadow)) {
        castShadow = !castShadow
    }

    val receiveShadowLabel = stringManager.getString("component.RenderComponent.receiveShadow")
    if (ImGui.checkbox(receiveShadowLabel, receiveShadow)) {
        receiveShadow = !receiveShadow
    }

    if (material != null) {
        MImGui.colorPicker4(stringManager.getString("material.baseColor"), material.baseColor)
        MImGui.drawVec4Control(stringManager.getString("material.baseColorFactor"), material.baseColorFactor)
        material.metallicFactor =
            MImGui.dragFloat(stringManager.getString("material.metallicFactor"), material.metallicFactor)
        material.roughnessFactor =
            MImGui.dragFloat(stringManager.getString("material.roughnessFactor"), material.roughnessFactor)
        MImGui.drawVec3Control(stringManager.getString("material.emissiveFactor"), material.emissiveFactor)
        val doubleSidedLabel = stringManager.getString("material.doubleSided")
        if (ImGui.checkbox(doubleSidedLabel, material.doubleSided)) {
            material.doubleSided = !material.doubleSided
        }
        MImGui.enumDropdown(stringManager.getString("material.alphaMode"), material.alphaMode)
        material.alphaCutoff = MImGui.dragFloat(stringManager.getString("material.alphaCutoff"), material.alphaCutoff)
    }
}