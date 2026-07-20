package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.TrickAnalyzer
import imgui.ImGui

fun TrickAnalyzer.imgui(stringManager: StringManager, logger: LoggerService) {
    ImGui.text(stringManager.getString("lbl.trick.current", lastTrickName))
    if (isAirborne) {
        ImGui.text(
            "Rotation: %.1f, %.1f, %.1f".format(
                currentAirRotation.x,
                currentAirRotation.y,
                currentAirRotation.z
            )
        )
    }
}