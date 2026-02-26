package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.CreateGameObjectCommand
import com.pafoid.skate.editor.systems.DeleteGameObjectCommand
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.windows.KeyBindingsWindow
import com.pafoid.skate.editor.windows.SettingsWindow
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.utils.UnitSystem
import com.pafoid.skate.game.level.LevelManager
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.beginMenuBar
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.endMenuBar
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.sliderFloat
import imgui.type.ImBoolean
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW

/**
 * Renders the main editor menu bar with File, Edit, Settings, and View menus.
 *
 * @param stringManager Localization for menu labels
 * @param levelManager Level save/load operations
 * @param undoRedoManager Undo/redo and clipboard operations
 * @param clipboardService Clipboard copy/paste
 * @param sceneManager Current scene access
 * @param settingsManager Settings access and persistence
 * @param keyBindingsWindow Open on menu click
 * @param settingsWindow Open on menu click
 * @param editorWindows List of windows for View menu checkboxes
 * @param glfwWindow GLFW window handle for closing
 * @param setFullscreen Fullscreen toggle callback
 * @param setVSync VSync toggle callback
 */
class EditorMenuBar(
    private val stringManager: StringManager,
    private val levelManager: LevelManager,
    private val undoRedoManager: UndoRedoManager,
    private val clipboardService: ClipboardService,
    private val sceneManager: SceneManager,
    private val settingsManager: SettingsManager,
    private val keyBindingsWindow: KeyBindingsWindow,
    private val settingsWindow: SettingsWindow,
    private val editorWindows: List<EditorWindow>,
    private val glfwWindow: Long,
    private val setFullscreen: (Boolean) -> Unit,
    private val setVSync: (Boolean) -> Unit
) {

    /**
     * Renders the complete menu bar.
     * Call this inside the dockspace window after begin() and before dockSpace().
     */
    fun render(currentScene: Scene) {
        if (beginMenuBar()) {
            buildFileMenu(currentScene)
            buildEditMenu(currentScene)
            buildSettingsMenu()
            buildViewMenu()
            endMenuBar()
        }
    }

    /**
     * Builds the File menu with save/load/open and quit options.
     */
    private fun buildFileMenu(currentScene: Scene) {
        if (beginMenu(stringManager.getString("menu.file"))) {
            if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save")}", "Ctrl+S")) {
                levelManager.save(currentScene)
            }
            if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_as")}")) {
                levelManager.saveAs(currentScene)
            }
            if (menuItem("${Icons.FOLDER_OPEN} ${stringManager.getString("menu.file.open")}", "Ctrl+O")) {
                levelManager.open(currentScene)
            }
            separator()
            if (menuItem("${Icons.TRASH} ${stringManager.getString("menu.file.quit")}")) {
                GLFW.glfwSetWindowShouldClose(glfwWindow, true)
            }
            endMenu()
        }
    }

    /**
     * Builds the Edit menu with undo/redo and clipboard operations.
     */
    private fun buildEditMenu(currentScene: Scene) {
        if (beginMenu(stringManager.getString("menu.edit"))) {
            if (menuItem("${Icons.UNDO} ${stringManager.getString("menu.edit.undo")}", "Ctrl+Z")) {
                undoRedoManager.undo()
            }
            if (menuItem("${Icons.REDO} ${stringManager.getString("menu.edit.redo")}", "Ctrl+Y")) {
                undoRedoManager.redo()
            }
            separator()
            if (menuItem("${Icons.CUT} ${stringManager.getString("menu.edit.cut")}", "Ctrl+X")) {
                val scene = sceneManager.currentScene
                val selected = scene?.getSelectedGameObject()
                if (selected != null) {
                    clipboardService.copy(selected)
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(selected, scene))
                }
            }
            if (menuItem("${Icons.COPY} ${stringManager.getString("menu.edit.copy")}", "Ctrl+C")) {
                sceneManager.currentScene?.getSelectedGameObject()?.let {
                    clipboardService.copy(it)
                }
            }
            if (menuItem("${Icons.PASTE} ${stringManager.getString("menu.edit.paste")}", "Ctrl+V")) {
                val cloned = clipboardService.paste()
                val scene = sceneManager.currentScene
                if (cloned != null) {
                    cloned.getComponent<Transform>()?.translation?.set(0f, 0f, 0f)
                    cloned.parent = null
                    scene?.let { undoRedoManager.executeCommand(CreateGameObjectCommand(cloned, it)) }
                }
            }
            endMenu()
        }
    }

    /**
     * Builds the Settings menu with display, input, and localization options.
     */
    private fun buildSettingsMenu() {
        if (beginMenu(stringManager.getString("menu.settings"))) {
            val settings = settingsManager.settings

            // Display settings
            val vsync = ImBoolean(settings.vsync)
            if (checkbox(stringManager.getString("menu.settings.vsync"), vsync)) {
                settings.vsync = vsync.get()
                setVSync(settings.vsync)
                settingsManager.save()
            }

            val fullscreen = ImBoolean(settings.fullscreen)
            if (checkbox(stringManager.getString("menu.settings.fullscreen"), fullscreen)) {
                settings.fullscreen = fullscreen.get()
                setFullscreen(settings.fullscreen)
                settingsManager.save()
            }

            separator()

            // Gamepad overlay settings
            val overlaySize = floatArrayOf(settings.gamepadOverlaySize)
            if (sliderFloat(
                    stringManager.getString("menu.settings.gamepad_overlay_size"),
                    overlaySize,
                    0.05f,
                    0.5f
                )
            ) {
                settings.gamepadOverlaySize = overlaySize[0]
                settingsManager.save()
            }

            val showOverlay = ImBoolean(settings.showGamepadOverlay)
            if (checkbox(stringManager.getString("menu.settings.show_gamepad_overlay"), showOverlay)) {
                settings.showGamepadOverlay = showOverlay.get()
                settingsManager.save()
            }

            separator()

            // Unit system
            val unitSystems = UnitSystem.entries.toTypedArray()
            val currentUnitIdx = ImInt(settings.unitSystem.ordinal)
            if (combo(
                    stringManager.getString("menu.settings.unit_system"),
                    currentUnitIdx,
                    unitSystems.map { it.name }.toTypedArray()
                )
            ) {
                settings.unitSystem = unitSystems[currentUnitIdx.get()]
                settingsManager.save()
            }

            separator()

            // Language selection
            val languages = arrayOf("en", "fr")
            val currentLangIdx = ImInt(languages.indexOf(settings.language))
            if (combo(
                    stringManager.getString("menu.settings.language"),
                    currentLangIdx,
                    languages,
                    languages.size
                )
            ) {
                val newLang = languages[currentLangIdx.get()]
                settings.language = newLang
                settingsManager.setLocale(newLang)
                settingsManager.save()
            }

            separator()

            // Open settings windows
            if (menuItem(stringManager.getString("menu.settings.keybindings"))) {
                keyBindingsWindow.isOpen = true
            }
            if (menuItem(stringManager.getString("menu.settings.settings"))) {
                settingsWindow.isOpen = true
            }

            endMenu()
        }
    }

    /**
     * Builds the View menu with window visibility toggles.
     */
    private fun buildViewMenu() {
        if (beginMenu(stringManager.getString("menu.view"))) {
            if (beginMenu(stringManager.getString("menu.view.windows"))) {
                // Render checkbox for each editor window
                editorWindows.forEach { window ->
                    checkbox(stringManager.getString(window.nameKey), window.showFlag)
                }
                endMenu()
            }
            endMenu()
        }
    }
}
