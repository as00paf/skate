package com.pafoid.skate.editor.ui.menus

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
        if (ImGui.beginMenu(stringManager.getString("menu.input"))) {
            if (ImGui.menuItem(stringManager.getString("menu.input.testing"), SHORTCUT_LABEL)) openInputTesting()
            ImGui.endMenu()
        }
    }

    /** Call every frame; the menu itself is only rendered while its popup is open. */
    fun handleShortcut() {
        val io = ImGui.getIO()
        if (io.keyCtrl && io.keyShift && ImGui.isKeyPressed(GLFW.GLFW_KEY_I, false)) openInputTesting()
    }

    private fun openInputTesting() = eventSystem.publish(WindowAction.Show(INPUT_TESTING))

    companion object {
        const val INPUT_TESTING = "window.input_testing"
        const val SHORTCUT_LABEL = "Ctrl+Shift+I"
    }
}
