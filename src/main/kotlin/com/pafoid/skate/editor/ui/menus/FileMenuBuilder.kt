package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.events.SceneAction.CreateRequested
import com.pafoid.skate.engine.events.SceneAction.SaveAsRequested
import com.pafoid.skate.engine.events.SceneAction.SaveRequested
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.menuItem

/**
 * Builds the File menu with scene management and application options.
 *
 * This component handles:
 * - Scene action event publication
 * - Quit application
 *
 * @param stringManager For localized menu strings
 * @param eventSystem For publishing scene actions
 */
class FileMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) {
    fun render(currentScene: Scene?, project: Project?) {
        if (beginMenu(stringManager.getString("menu.file"))) {
            renderNewSceneItem()
            renderSaveSceneItems(currentScene)
            renderImportSceneItem()
            endMenu()
        }
    }

    private fun renderNewSceneItem() {
        if (menuItem("${Icons.PLUS} ${stringManager.getString("menu.file.new_scene")}", "Ctrl+N")) {
            eventSystem.publish(CreateRequested)
        }
    }

    private fun renderSaveSceneItems(currentScene: Scene?) {
        if (currentScene == null) return

        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_scene")}", "Ctrl+S")) {
            eventSystem.publish(SaveRequested(currentScene))
        }
        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_scene_as")}")) {
            eventSystem.publish(SaveAsRequested(currentScene))
        }
    }

    private fun renderImportSceneItem() {
        // TODO: add import icon
        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.import_scene")}", "Ctrl+I")) {
            eventSystem.publish(SceneAction.ImportRequested)
        }
    }
}
