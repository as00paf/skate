package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.data.Icons
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

class FileMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) {
    fun render(currentScene: Scene?) {
        if (beginMenu(stringManager.label("menu.file"))) {
            renderNewSceneItem()
            renderSaveSceneItems(currentScene)
            renderImportSceneItem()
            endMenu()
        }
    }

    private fun renderNewSceneItem() {
        if (stringManager.tipped(menuItem("${Icons.PLUS} ${stringManager.getString("menu.file.new_scene")}", "Ctrl+N"), "menu.file.new_scene")) {
            eventSystem.publish(CreateRequested)
        }
    }

    private fun renderSaveSceneItems(currentScene: Scene?) {
        if (currentScene == null) return

        if (stringManager.tipped(menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_scene")}", "Ctrl+S"), "menu.file.save_scene")) {
            eventSystem.publish(SaveRequested(currentScene))
        }
        if (stringManager.tipped(menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_scene_as")}"), "menu.file.save_scene_as")) {
            eventSystem.publish(SaveAsRequested(currentScene))
        }
    }

    private fun renderImportSceneItem() {
        if (stringManager.tipped(menuItem("${Icons.IMPORT_FILE} ${stringManager.getString("menu.file.import_scene")}", "Ctrl+I"), "menu.file.import_scene")) {
            eventSystem.publish(SceneAction.ImportRequested)
        }
    }
}
