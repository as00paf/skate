package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.events.ScriptEditorAction
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.ui.handlers.ScriptEditorActionHandler
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import imgui.ImGui
import imgui.type.ImString
import java.io.File

private val pathBuffers = HashMap<ScriptComponent, ImString>()

fun ScriptComponent.imgui(engine: Engine) {
    val strings = engine.stringManager
    ImGui.pushID(System.identityHashCode(this))
    val buffer = pathBuffers.getOrPut(this) { ImString(path, 512) }
    if (!ImGui.isAnyItemActive() && buffer.get() != path) buffer.set(path)
    if (ImGui.inputText(strings.getString("lbl.script_editor.path"), buffer)) path = buffer.get()
    if (ImGui.button(strings.getString("btn.script_editor.edit"))) {
        val file: File = File(path)
        engine.eventSystem.publish(ScriptEditorAction.Open(file))
        engine.eventSystem.publish(WindowAction.Show(ScriptEditorActionHandler.WINDOW))
    }
    ImGui.popID()
}
