package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.RenderComponent
import imgui.ImGui

// TODO: use event system/commands to support undo/redo
fun RenderComponent.imgui(engine: Engine) {
    textureScale = MImGui.dragFloat(
        engine.stringManager.getString("component.RenderComponent.textureScale"),
        textureScale
    )

    renderMode = MImGui.enumDropdown(engine.stringManager.getString("component.RenderComponent.renderMode"), renderMode)

    val castShadowLabel = engine.stringManager.getString("component.RenderComponent.castShadow")
    if (ImGui.checkbox(castShadowLabel, castShadow)) {
        castShadow = !castShadow
    }

    val receiveShadowLabel = engine.stringManager.getString("component.RenderComponent.receiveShadow")
    if (ImGui.checkbox(receiveShadowLabel, receiveShadow)) {
        receiveShadow = !receiveShadow
    }

    val mat = material ?: model?.mesh?.get(0)?.material
    if (mat != null) {
        MImGui.colorPicker4(engine.stringManager.getString("material.baseColor"), mat.baseColor)
        mat.baseColorFactor =
            MImGui.dragFloat(engine.stringManager.getString("material.baseColorFactor"), mat.baseColorFactor)
        mat.metallicFactor =
            MImGui.dragFloat(engine.stringManager.getString("material.metallicFactor"), mat.metallicFactor)
        mat.roughnessFactor =
            MImGui.dragFloat(engine.stringManager.getString("material.roughnessFactor"), mat.roughnessFactor)
        MImGui.drawVec3Control(engine.stringManager.getString("material.emissiveFactor"), mat.emissiveFactor)
        val doubleSidedLabel = engine.stringManager.getString("material.doubleSided")
        if (ImGui.checkbox(doubleSidedLabel, mat.doubleSided)) {
            mat.doubleSided = !mat.doubleSided
        }
        MImGui.enumDropdown(engine.stringManager.getString("material.alphaMode"), mat.alphaMode)
        mat.alphaCutoff = MImGui.dragFloat(engine.stringManager.getString("material.alphaCutoff"), mat.alphaCutoff)

        // Textures
        val defaultValue = engine.stringManager.getString("material.texture.select")
        mat.baseColorTexture = MImGui.textureDropdown(
            engine.stringManager.getString("material.baseColorTexture"),
            mat.baseColorTexture,
            engine.assetsManager,
            defaultValue
        )
        mat.metallicRoughnessTexture = MImGui.textureDropdown(
            engine.stringManager.getString("material.metallicRoughnessTexture"),
            mat.metallicRoughnessTexture,
            engine.assetsManager,
            defaultValue
        )
        mat.emissiveTexture = MImGui.textureDropdown(
            engine.stringManager.getString("material.emissiveTexture"),
            mat.emissiveTexture,
            engine.assetsManager,
            defaultValue
        )
        mat.aoTexture = MImGui.textureDropdown(
            engine.stringManager.getString("material.aoTexture"),
            mat.aoTexture,
            engine.assetsManager,
            defaultValue
        )
    }
}