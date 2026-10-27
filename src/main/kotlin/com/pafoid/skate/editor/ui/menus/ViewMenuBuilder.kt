package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.menuItem

class ViewMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val windowRegistry: WindowRegistry,
) {

    fun render() {
        if (beginMenu(stringManager.getString("menu.view"))) {
            if (beginMenu(stringManager.getString("menu.view.windows"))) {
                windowRegistry.dockableWindows.forEach { window ->
                    checkbox(stringManager.getString(window.name), window.isOpen)
                }
                endMenu()
            }
            if (menuItem(stringManager.getString("menu.view.reset_layout"))) {
                eventSystem.publish(WindowAction.ResetLayout)
            }
            endMenu()
        }
    }
}
