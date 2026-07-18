package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.UndoRedoAction
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.SceneManager
import imgui.ImGui
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.menuItem

/**
 * Builds the Edit menu with undo/redo and clipboard operations.
 * 
 * This component handles:
 * - Undo/Redo operations
 * - Cut/Copy/Paste operations
 * - Selection event publishing
 * 
 * @param stringManager For localized menu strings
 * @param clipboardService For clipboard operations
 * @param sceneManager For accessing current scene and selection
 * @param eventSystem For publishing selection events
 */
class EditMenuBuilder(
    private val stringManager: StringManager,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) {

    fun render() {
        if (beginMenu(stringManager.getString("menu.edit"))) {
            renderUndoRedoItems()
            ImGui.separator()
            renderClipboardItems()
            endMenu()
        }
    }
    
    private fun renderUndoRedoItems() {
        if (menuItem("${Icons.UNDO} ${stringManager.getString("menu.edit.undo")}", "Ctrl+Z")) {
            eventSystem.publish(UndoRedoAction.Undo)
        }
        if (menuItem("${Icons.REDO} ${stringManager.getString("menu.edit.redo")}", "Ctrl+Y")) {
            eventSystem.publish(UndoRedoAction.Redo)
        }
    }
    
    private fun renderClipboardItems() {
        if (menuItem("${Icons.CUT} ${stringManager.getString("menu.edit.cut")}", "Ctrl+X")) {
            val scene = sceneManager.currentScene
            val selected = scene?.selectedGameObject
            if (selected != null) {
                eventSystem.publish(ViewportAction.CopyClipboard(selected))
                eventSystem.publish(ViewportAction.Delete(selected, scene))
                eventSystem.publish(ViewportAction.SelectionCleared)
            }
        }
        if (menuItem("${Icons.COPY} ${stringManager.getString("menu.edit.copy")}", "Ctrl+C")) {
            sceneManager.currentScene?.selectedGameObject?.let {
                eventSystem.publish(ViewportAction.CopyClipboard(it))
            }
        }
        if (menuItem("${Icons.PASTE} ${stringManager.getString("menu.edit.paste")}", "Ctrl+V")) {
            eventSystem.publish(ViewportAction.PasteClipboard())
        }
        if (menuItem("${Icons.PASTE} ${stringManager.getString("menu.edit.cut")}", "Ctrl+X")) {
            sceneManager.currentScene?.selectedGameObject?.let {
                eventSystem.publish(ViewportAction.CutClipboard(it))
            }
        }
    }
}
