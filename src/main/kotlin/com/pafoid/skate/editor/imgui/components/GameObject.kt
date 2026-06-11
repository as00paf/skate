package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import imgui.ImGui

fun GameObject.imGui(stringManager: StringManager) {
    components.forEach {
        if (ImGui.collapsingHeader(it.javaClass.simpleName))
            it.imgui(stringManager)
    }
}