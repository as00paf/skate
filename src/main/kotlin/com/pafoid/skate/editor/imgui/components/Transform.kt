package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.events.TransformAction
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.Transform
import imgui.ImGui

/** Groups inspector edits into undo entries; the inspector shows one Transform at a time. */
private val editTracker = TransformEditTracker()

fun Transform.imgui(engine: Engine) {
    editTracker.beforeDraw(this)
    drawControls(engine)
    val go = gameObject ?: return
    val interacting = ImGui.isAnyItemActive() || ImGui.isMouseDown(0)
    editTracker.afterDraw(go, this, interacting).forEach { engine.eventSystem.publish(TransformAction.Edited(it)) }
}

private fun Transform.drawControls(engine: Engine) {
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