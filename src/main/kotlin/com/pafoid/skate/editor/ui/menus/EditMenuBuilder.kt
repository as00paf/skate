package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.UndoRedoAction
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.HamburgerMenuRenderer
import com.pafoid.skate.editor.imgui.MenuAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.SceneManager

/** Edit: undo/redo, clipboard, Duplicate and Delete on the selection, and Search Anywhere (EDT-51 §2.7). */
class EditMenuBuilder(
    private val stringManager: StringManager,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) {
    fun render() = HamburgerMenuRenderer.menu(stringManager, HamburgerMenuModel.EDIT, ::action)

    private fun action(id: String): MenuAction? {
        val scene = sceneManager.currentScene
        val selected = scene?.selectedGameObject
        val hasSelection = selected != null
        return when (id) {
            "undo" -> MenuAction { eventSystem.publish(UndoRedoAction.Undo) }
            "redo" -> MenuAction { eventSystem.publish(UndoRedoAction.Redo) }
            "cut" -> MenuAction(hasSelection) {
                if (selected != null) {
                    eventSystem.publish(ViewportAction.CopyClipboard(selected))
                    eventSystem.publish(ViewportAction.Delete(selected, scene))
                    eventSystem.publish(ViewportAction.SelectionCleared)
                }
            }
            "copy" -> MenuAction(hasSelection) { selected?.let { eventSystem.publish(ViewportAction.CopyClipboard(it)) } }
            "paste" -> MenuAction(scene != null) { eventSystem.publish(ViewportAction.PasteClipboard()) }
            "duplicate" -> MenuAction(hasSelection) { selected?.let { eventSystem.publish(ViewportAction.Duplicate(it)) } }
            "delete" -> MenuAction(hasSelection) {
                if (selected != null) eventSystem.publish(ViewportAction.Delete(selected, scene))
            }
            "search" -> MenuAction { eventSystem.publish(WindowAction.Show("window.search")) }
            else -> null
        }
    }
}
