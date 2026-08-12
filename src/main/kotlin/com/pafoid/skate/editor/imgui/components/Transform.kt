package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.Transform

fun Transform.imgui(engine: Engine) {
    MImGui.drawVec3TransformControl(engine.stringManager.getString("component.Transform.translation"), translation)
    MImGui.drawVec3TransformControl(
        engine.stringManager.getString("component.Transform.rotation"),
        rotation,
        0f,
        MImGui.SENSIBILITY_ROTATION
    )
    MImGui.drawVec3TransformControl(
        engine.stringManager.getString("component.Transform.scale"),
        scale,
        1f,
        MImGui.SENSIBILITY_SCALE,
        true
    )
}