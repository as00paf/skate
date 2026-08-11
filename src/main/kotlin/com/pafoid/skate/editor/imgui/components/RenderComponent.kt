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

    val mat = material ?: model?.mesh?.get(0)?.material
    if (mat != null) {
        MImGui.colorPicker4(stringManager.getString("material.baseColor"), mat.baseColor)
        mat.baseColorFactor =
            MImGui.dragFloat(stringManager.getString("material.baseColorFactor"), mat.baseColorFactor)
        mat.metallicFactor =
            MImGui.dragFloat(stringManager.getString("material.metallicFactor"), mat.metallicFactor)
        mat.roughnessFactor =
            MImGui.dragFloat(stringManager.getString("material.roughnessFactor"), mat.roughnessFactor)
        MImGui.drawVec3Control(stringManager.getString("material.emissiveFactor"), mat.emissiveFactor)
        val doubleSidedLabel = stringManager.getString("material.doubleSided")
        if (ImGui.checkbox(doubleSidedLabel, mat.doubleSided)) {
            mat.doubleSided = !mat.doubleSided
        }
        MImGui.enumDropdown(stringManager.getString("material.alphaMode"), mat.alphaMode)
        mat.alphaCutoff = MImGui.dragFloat(stringManager.getString("material.alphaCutoff"), mat.alphaCutoff)
    }
}