package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import imgui.ImGui

fun GameObject.imgui(engine: Engine) {
    components.forEach {
        if (ImGui.collapsingHeader(it.javaClass.simpleName))
            it.imgui(engine)
    }
}