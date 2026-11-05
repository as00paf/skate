package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import org.lwjgl.glfw.GLFW

class InputMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) {
    fun render() {
        if (ImGui.beginMenu(stringManager.label("menu.input"))) {
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString("menu.input.testing"), EditorShortcuts.INPUT_TESTING.display()), "menu.input.testing", EditorShortcuts.INPUT_TESTING)) openInputTesting()
            ImGui.endMenu()
        }
    }

    /** Call every frame; the menu itself is only rendered while its popup is open. */
    fun handleShortcut() {
        val io = ImGui.getIO()
        val combo = EditorShortcuts.INPUT_TESTING.combo()
        if (io.keyCtrl == combo.ctrl && io.keyShift == combo.shift && ImGui.isKeyPressed(combo.key, false)) openInputTesting()
    }

    private fun openInputTesting() = eventSystem.publish(WindowAction.Show(INPUT_TESTING))

    companion object {
        const val INPUT_TESTING = "window.input_testing"
    }
}
