package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.PlayerStateManager
import imgui.ImGui

fun PlayerStateManager.imgui(stringManager: StringManager, logger: LoggerService) {
    val currentStateText = currentState::class.simpleName.orEmpty()

    ImGui.text(stringManager.getString("lbl.player.state", currentStateText))
    ImGui.text(stringManager.getString("lbl.player.current_stance", currentStance))
    ImGui.text(stringManager.getString("lbl.player.is_switch", isSwitch))
    if (ImGui.button(stringManager.getString("btn.player.toggle_switch"))) {
        isSwitch = !isSwitch
    }
}