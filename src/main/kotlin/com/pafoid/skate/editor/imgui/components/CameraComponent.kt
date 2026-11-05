package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.CameraComponent
import imgui.ImGui


fun CameraComponent.imgui(engine: Engine) {
    // Position and rotation are edited in the Transform inspector, which records them for undo (EDT-10).
    fov = MImGui.dragFloat(engine.stringManager.getString("component.CameraComponent.fov"), fov)
    nearPlane = MImGui.dragFloat(engine.stringManager.getString("component.CameraComponent.nearPlane"), nearPlane)
    farPlane = MImGui.dragFloat(engine.stringManager.getString("component.CameraComponent.farPlane"), farPlane)
    zoom = MImGui.dragFloat(engine.stringManager.getString("component.CameraComponent.zoom"), zoom)

    val isOrthographicLabel = engine.stringManager.getString("component.CameraComponent.isOrthographic")
    if (ImGui.checkbox(isOrthographicLabel, isOrthographic)) {
        isOrthographic = !isOrthographic
    }

    val isDefaultLabel = engine.stringManager.getString("component.CameraComponent.isDefault")
    if (ImGui.checkbox(isDefaultLabel, isDefault)) {
        isDefault = !isDefault
    }

}