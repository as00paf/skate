package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.engine.core.StringManager
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.endMenu

class ViewMenuBuilder(
    private val stringManager: StringManager,
    private val windowRegistry: WindowRegistry,
) {

    fun render() {
        if (beginMenu(stringManager.getString("menu.view"))) {
            if (beginMenu(stringManager.getString("menu.view.windows"))) {
                windowRegistry.windows.forEach { window ->
                    checkbox(stringManager.getString(window.name), window.isOpen)
                }
                endMenu()
            }
            endMenu()
        }
    }
}
