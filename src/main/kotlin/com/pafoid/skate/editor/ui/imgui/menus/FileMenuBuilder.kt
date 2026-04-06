package com.pafoid.skate.editor.ui.imgui.menus

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.game.level.LevelManager
import imgui.ImGui
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.separator
import org.lwjgl.glfw.GLFW

/**
 * Builds the File menu with scene management and application options.
 * 
 * This component handles:
 * - New Scene creation
 * - Save/Save As operations
 * - Open Scene dialog
 * - Quit application
 * 
 * @param stringManager For localized menu strings
 * @param levelManager For scene save/load operations
 * @param sceneManager For opening new scenes
 */
class FileMenuBuilder(
    private val stringManager: StringManager,
    private val levelManager: LevelManager,
    private val sceneManager: com.pafoid.skate.engine.ecs.SceneManager,
    private val glfwWindow: Long,
    private val sceneInitializer: com.pafoid.skate.editor.LevelEditorSceneInitializer
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
            ImGui.separator()
            renderQuitItem()
            endMenu()
        }
    }
    
    private fun renderNewSceneItem() {
        if (menuItem("${Icons.PLUS} New Scene", "Ctrl+N")) {
            JobSystem.runOnMain {
                val newScene = Scene("New Scene", sceneInitializer)
                newScene.init()
                sceneManager.openScene(newScene)
            }
        }
    }
    
    private fun renderSaveItems(currentScene: Scene) {
        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save")}", "Ctrl+S")) {
            levelManager.save(currentScene)
        }
        if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_as")}")) {
            levelManager.saveAs(currentScene)
        }
    }
    
    private fun renderOpenItem() {
        if (menuItem("${Icons.FOLDER_OPEN} ${stringManager.getString("menu.file.open")}", "Ctrl+O")) {
            JobSystem.runOnMain {
                val newScene = Scene("Loaded Scene", sceneInitializer)
                newScene.init()
                sceneManager.openScene(newScene)
                levelManager.open(newScene)
            }
        }
    }
    
    private fun renderQuitItem() {
        if (menuItem("${Icons.TRASH} ${stringManager.getString("menu.file.quit")}")) {
            GLFW.glfwSetWindowShouldClose(glfwWindow, true)
        }
    }
}
