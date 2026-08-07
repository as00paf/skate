package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import imgui.ImGui

fun GameObject.imgui(stringManager: StringManager, eventSystem: EventSystem, logger: LoggerService) {
    components.forEach {
        if (ImGui.collapsingHeader(it.javaClass.simpleName))
            it.imgui(stringManager, eventSystem, logger)
    }
}