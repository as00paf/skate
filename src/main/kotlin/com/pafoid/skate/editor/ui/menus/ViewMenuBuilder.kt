package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.WindowCategory
import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.textDisabled

class ViewMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val windowRegistry: WindowRegistry,
) {

    fun render() {
        if (beginMenu(stringManager.getString("menu.view"))) {
            if (beginMenu(stringManager.getString("menu.view.windows"))) {
                val grouped = WindowCategory.grouped(windowRegistry.dockableWindows)
                grouped.entries.forEachIndexed { index, (category, windows) ->
                    if (index > 0) separator()
                    textDisabled(stringManager.getString(category.labelKey))
                    windows.forEach { window -> checkbox(stringManager.getString(window.name), window.isOpen) }
                }
                separator()
                if (menuItem(stringManager.getString("menu.view.windows.open_all"))) {
                    grouped.values.flatten().forEach { it.isOpen.set(true) }
                }
                if (menuItem(stringManager.getString("menu.view.windows.close_all"))) {
                    grouped.values.flatten().forEach { it.isOpen.set(false) }
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
