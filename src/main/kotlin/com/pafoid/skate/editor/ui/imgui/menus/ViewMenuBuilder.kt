package com.pafoid.skate.editor.ui.imgui.menus

import com.pafoid.skate.editor.imgui.data.EditorWindow
import com.pafoid.skate.editor.systems.StringManager
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.endMenu

/**
 * Builds the View menu with window visibility toggles.
 * 
 * This component handles:
 * - Window visibility checkboxes
 * - Editor window list from registry
 * 
 * @param stringManager For localized menu strings
 * @param editorWindows List of editor windows for toggling
 */
class ViewMenuBuilder(
    private val stringManager: StringManager,
    private val editorWindows: List<EditorWindow>
) {
    
    /**
     * Renders the View menu.
     */
    fun render() {
        if (beginMenu(stringManager.getString("menu.view"))) {
            if (beginMenu(stringManager.getString("menu.view.windows"))) {
                editorWindows.forEach { window ->
                    checkbox(stringManager.getString(window.nameKey), window.showFlag)
                }
                endMenu()
            }
            endMenu()
        }
    }
}
