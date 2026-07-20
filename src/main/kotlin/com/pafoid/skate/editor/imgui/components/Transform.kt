package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.Transform

fun Transform.imgui(stringManager: StringManager, logger: LoggerService) {
    MImGui.drawVec3TransformControl("Position", translation)
    MImGui.drawVec3TransformControl("Rotation", rotation, 0f, MImGui.SENSIBILITY_ROTATION)
    MImGui.drawVec3TransformControl("Scale", scale, 1f, MImGui.SENSIBILITY_SCALE, true)
}