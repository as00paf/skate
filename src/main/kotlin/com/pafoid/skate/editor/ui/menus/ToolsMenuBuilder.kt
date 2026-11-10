package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.HamburgerMenuRenderer
import com.pafoid.skate.editor.imgui.MenuAction
import com.pafoid.skate.editor.ui.windows.KeyBindingsWindow
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/** Tools: Test Input… (replaces the old one-item Input menu) and the Shell (EDT-51 §2.7). */
class ToolsMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) {
    fun render() = HamburgerMenuRenderer.menu(stringManager, HamburgerMenuModel.TOOLS, ::action)

    private fun action(id: String): MenuAction? = when (id) {
        "test_input" -> MenuAction { openTestInput() }
        "shell" -> MenuAction { eventSystem.publish(WindowAction.Show(SHELL)) }
        else -> null
    }

    /** Call every frame: the Test Input shortcut works while the menu is closed. */
    fun handleShortcut() {
        val io = ImGui.getIO()
        val combo = EditorShortcuts.INPUT_TESTING.combo()
        if (io.keyCtrl == combo.ctrl && io.keyShift == combo.shift && ImGui.isKeyPressed(combo.key, false)) openTestInput()
    }

    /** Input Testing lives in Key Bindings › Test (EDT-52I). */
    private fun openTestInput() = eventSystem.publish(WindowAction.Show(KeyBindingsWindow.TEST_TAB))

    companion object {
        const val SHELL = "window.shell"
    }
}
