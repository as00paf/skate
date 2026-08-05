package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.PlayerStateManager
import imgui.ImGui

fun PlayerStateManager.imgui(stringManager: StringManager, logger: LoggerService) {
    // TODO: should be rendered as a user customizable list
    ImGui.text(stringManager.getString("lbl.player.state", currentState.toString()))
}