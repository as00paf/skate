package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
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

class EditMenuBuilder(
    private val stringManager: StringManager,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) {

    fun render() {
        if (beginMenu(stringManager.label("menu.edit"))) {
            renderUndoRedoItems()
            ImGui.separator()
            renderClipboardItems()
            endMenu()
        }
    }
    
    private fun renderUndoRedoItems() {
        if (stringManager.tipped(menuItem("${Icons.UNDO} ${stringManager.getString("menu.edit.undo")}", "Ctrl+Z"), "menu.edit.undo")) {
            eventSystem.publish(UndoRedoAction.Undo)
        }
        if (stringManager.tipped(menuItem("${Icons.REDO} ${stringManager.getString("menu.edit.redo")}", "Ctrl+Y"), "menu.edit.redo")) {
            eventSystem.publish(UndoRedoAction.Redo)
        }
    }
    
    private fun renderClipboardItems() {
        if (stringManager.tipped(menuItem("${Icons.CUT} ${stringManager.getString("menu.edit.cut")}", "Ctrl+X"), "menu.edit.cut")) {
            val scene = sceneManager.currentScene
            val selected = scene?.selectedGameObject
            if (selected != null) {
                eventSystem.publish(ViewportAction.CopyClipboard(selected))
                eventSystem.publish(ViewportAction.Delete(selected, scene))
                eventSystem.publish(ViewportAction.SelectionCleared)
            }
        }
        if (stringManager.tipped(menuItem("${Icons.COPY} ${stringManager.getString("menu.edit.copy")}", "Ctrl+C"), "menu.edit.copy")) {
            sceneManager.currentScene?.selectedGameObject?.let {
                eventSystem.publish(ViewportAction.CopyClipboard(it))
            }
        }
        if (stringManager.tipped(menuItem("${Icons.PASTE} ${stringManager.getString("menu.edit.paste")}", "Ctrl+V"), "menu.edit.paste")) {
            eventSystem.publish(ViewportAction.PasteClipboard())
        }
    }
}
