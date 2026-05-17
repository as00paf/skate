package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.SceneAction
import com.pafoid.skate.editor.events.SceneAction.*
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.separator
import org.lwjgl.glfw.GLFW

/**
 * Builds the File menu with scene management and application options.
 *
 * This component handles:
 * - Scene action event publication
 * - Quit application
 *
 * @param stringManager For localized menu strings
 * @param eventSystem For publishing scene actions
 * @param sceneManager For active scene index lookup
 */
class FileMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val sceneManager: SceneManager,
    private val glfwWindow: Long
) {
    
    /**
     * Renders the File menu.
     * 
     * @param currentScene The current scene for save operations
     */
    fun render(currentScene: Scene) {
        if (beginMenu(stringManager.getString("menu.file"))) {
            renderNewSceneItem()
            renderSaveItems(currentScene)
            renderOpenItem()
            separator()
            renderQuitItem()
            endMenu()
        }
    }
    
    private fun renderNewSceneItem() {
        if (menuItem("${Icons.PLUS} ${stringManager.getString("menu.file.new_scene")}", "Ctrl+N")) {
            eventSystem.publish(CreateRequested)
        }
    }
    
    private fun renderSaveItems(currentScene: Scene) {
        if (!sceneManager.openScenes.contains(currentScene)) return

        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save")}", "Ctrl+S")) {
            eventSystem.publish(SaveRequested(currentScene))
        }
        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_as")}")) {
            eventSystem.publish(SaveAsRequested(currentScene))
        }
    }

    private fun renderOpenItem() {
        if (menuItem("${Icons.FOLDER_OPEN} ${stringManager.getString("menu.file.open")}", "Ctrl+O")) {
            eventSystem.publish(OpenRequested)
        }
    }
    
    private fun renderQuitItem() {
        if (menuItem("${Icons.TRASH} ${stringManager.getString("menu.file.quit")}")) {
            GLFW.glfwSetWindowShouldClose(glfwWindow, true)
        }
    }
}
