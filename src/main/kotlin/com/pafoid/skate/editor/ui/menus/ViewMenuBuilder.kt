package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.HamburgerMenuRenderer
import com.pafoid.skate.editor.imgui.MenuAction
import com.pafoid.skate.editor.imgui.WindowCategory
import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/** View: Windows ▸ by category, Open all, Close all, Reset layout (EDT-51 §2.7). */
class ViewMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val windowRegistry: WindowRegistry,
) {
    fun render() = HamburgerMenuRenderer.menu(stringManager, HamburgerMenuModel.VIEW, ::action) { renderWindows() }

    private fun dockable() = WindowCategory.grouped(windowRegistry.dockableWindows)

    private fun action(id: String): MenuAction? = when (id) {
        "open_all" -> MenuAction { dockable().values.flatten().forEach { it.isOpen.set(true) } }
        "close_all" -> MenuAction { dockable().values.flatten().forEach { it.isOpen.set(false) } }
        "reset_layout" -> MenuAction { eventSystem.publish(WindowAction.ResetLayout) }
        else -> null
    }

    /** One checkbox per window, grouped under its category heading. */
    private fun renderWindows() {
        dockable().entries.forEachIndexed { index, (category, windows) ->
            if (index > 0) ImGui.separator()
            ImGui.textDisabled(stringManager.getString(category.labelKey))
            windows.forEach { window -> ImGui.checkbox(stringManager.label(window.name), window.isOpen) }
        }
    }
}
