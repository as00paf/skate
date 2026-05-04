package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneCreateRequested
import com.pafoid.skate.engine.events.SceneOpenRequested
import com.pafoid.skate.engine.events.SceneSaveAsRequested
import com.pafoid.skate.engine.events.SceneSaveRequested
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
            eventSystem.publish(SceneCreateRequested)
        }
    }
    
    private fun renderSaveItems(currentScene: Scene) {
        val sceneIndex = sceneManager.openScenes.indexOf(currentScene)
        if (sceneIndex < 0) return

        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save")}", "Ctrl+S")) {
            eventSystem.publish(SceneSaveRequested(sceneIndex))
        }
        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_as")}")) {
            eventSystem.publish(SceneSaveAsRequested(sceneIndex))
        }
    }

    private fun renderOpenItem() {
        if (menuItem("${Icons.FOLDER_OPEN} ${stringManager.getString("menu.file.open")}", "Ctrl+O")) {
            eventSystem.publish(SceneOpenRequested)
        }
    }
    
    private fun renderQuitItem() {
        if (menuItem("${Icons.TRASH} ${stringManager.getString("menu.file.quit")}")) {
            GLFW.glfwSetWindowShouldClose(glfwWindow, true)
        }
    }
}
