package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.Transform

fun Transform.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    MImGui.drawVec3TransformControl(stringManager.getString("component.Transform.translation"), translation)
    MImGui.drawVec3TransformControl(
        stringManager.getString("component.Transform.rotation"),
        rotation,
        0f,
        MImGui.SENSIBILITY_ROTATION
    )
    MImGui.drawVec3TransformControl(
        stringManager.getString("component.Transform.scale"),
        scale,
        1f,
        MImGui.SENSIBILITY_SCALE,
        true
    )
}