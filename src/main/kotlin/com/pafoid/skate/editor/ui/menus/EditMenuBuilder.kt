package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.commands.scene.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.scene.DeleteGameObjectCommand
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.events.SelectionCleared
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
 * @param undoRedoManager For undo/redo operations
 * @param clipboardService For clipboard operations
 * @param sceneManager For accessing current scene and selection
 * @param eventSystem For publishing selection events
 */
class EditMenuBuilder(
    private val stringManager: StringManager,
    private val undoRedoManager: UndoRedoManager,
    private val clipboardService: ClipboardService,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
    private val gameObjectManager: GameObjectManager,
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
            undoRedoManager.undo()
        }
        if (menuItem("${Icons.REDO} ${stringManager.getString("menu.edit.redo")}", "Ctrl+Y")) {
            undoRedoManager.redo()
        }
    }
    
    private fun renderClipboardItems() {
        if (menuItem("${Icons.CUT} ${stringManager.getString("menu.edit.cut")}", "Ctrl+X")) {
            val scene = sceneManager.currentScene
            val selected = scene?.selectedGameObject
            if (selected != null) {
                clipboardService.copy(selected)
                undoRedoManager.executeCommand(DeleteGameObjectCommand(selected, scene, gameObjectManager))
                eventSystem.publish(SelectionCleared)
            }
        }
        if (menuItem("${Icons.COPY} ${stringManager.getString("menu.edit.copy")}", "Ctrl+C")) {
            sceneManager.currentScene?.selectedGameObject?.let {
                clipboardService.copy(it)
            }
        }
        if (menuItem("${Icons.PASTE} ${stringManager.getString("menu.edit.paste")}", "Ctrl+V")) {
            val cloned = clipboardService.paste()
            if (cloned != null) {
                cloned.getComponent<Transform>()?.translation?.set(0f, 0f, 0f)
                cloned.parent = null
                sceneManager.currentScene?.let {
                    undoRedoManager.executeCommand(
                        CreateGameObjectCommand(
                            cloned,
                            it,
                            gameObjectManager
                        )
                    )
                }
            }
        }
    }
}
