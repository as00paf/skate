package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Color
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.CreateGameObjectCommand
import com.pafoid.skate.editor.systems.DeleteGameObjectCommand
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.windows.KeyBindingsWindow
import com.pafoid.skate.editor.windows.SettingsWindow
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.WindowController
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.utils.UnitSystem
import com.pafoid.skate.game.level.LevelManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.beginMenuBar
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.endMenuBar
import imgui.internal.ImGui.image
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.popStyleColor
import imgui.internal.ImGui.popStyleVar
import imgui.internal.ImGui.pushStyleColor
import imgui.internal.ImGui.pushStyleVar
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
 * @param resourceManager To load app icon and other textures
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
    private val resourceManager: ResourceManager,
    private val keyBindingsWindow: KeyBindingsWindow,
    private val settingsWindow: SettingsWindow,
    private val editorWindows: List<EditorWindow>,
    private val glfwWindow: Long,
    private val setFullscreen: (Boolean) -> Unit,
    private val setVSync: (Boolean) -> Unit
) {
    private val windowController = WindowController(glfwWindow)
    private var appIconTexId = -1
    private val projectIcon = Icons.CUBE
    private val projectName = "Skate Project"

    init {
        // App Icon is loaded synchronously for immediate availability in the menu bar
        appIconTexId = resourceManager.loadTextureSync(Assets.Textures.APP_ICON).texId
    }

    /**
     * Renders the complete menu bar.
     * Call this inside the dockspace window after begin() and before dockSpace().
     */
    fun render(currentScene: Scene) {
        if (beginMenuBar()) {
            val barHeight = 48f

            renderAppIcon(barHeight)
            renderHamburgerMenu(currentScene, barHeight)
            renderProjectInfo(barHeight)
            handleDragging(barHeight)
            buildWindowControls(barHeight)

            endMenuBar()
        }
    }

    private fun renderAppIcon(barHeight: Float) {
        if (appIconTexId != -1) {
            val iconSize = 32f
            // Centering logic: (Bar Height - Icon Height) / 2
            ImGui.setCursorPosY((barHeight - iconSize) / 2f)
            image(appIconTexId.toLong(), iconSize, iconSize)
        }
    }

    private fun renderHamburgerMenu(currentScene: Scene, barHeight: Float) {
        val btnSize = 30f
        val offsetY = (barHeight - btnSize) / 2f
        ImGui.setCursorPosY(offsetY)

        // Use a square button as the menu trigger
        if (ImGui.button(Icons.MENU, btnSize, btnSize)) {
            ImGui.openPopup("main_hamburger_menu")
        }

        // Define the menu as a popup that appears below the button
        if (ImGui.beginPopup("main_hamburger_menu")) {
            buildFileMenu(currentScene)
            buildEditMenu(currentScene)
            buildSettingsMenu()
            buildViewMenu()
            ImGui.endPopup()
        }
    }

    private fun renderProjectInfo(barHeight: Float) {
        val fontSize = ImGui.getFontSize()
        val textY = (barHeight - fontSize) / 2f * 0.8f
        ImGui.setCursorPosY(textY)

        ImGui.textDisabled("|")
        ImGui.setCursorPosY(textY)
        ImGui.textColored(
            Color.ISLAND_ACCENT_BLUE.x,
            Color.ISLAND_ACCENT_BLUE.y,
            Color.ISLAND_ACCENT_BLUE.z,
            Color.ISLAND_ACCENT_BLUE.w,
            projectIcon
        )
        ImGui.setCursorPosY(textY)
        ImGui.text(projectName)
    }

    private fun handleDragging(barHeight: Float) {
        val mouseX = DoubleArray(1)
        val mouseY = DoubleArray(1)
        GLFW.glfwGetCursorPos(glfwWindow, mouseX, mouseY)

        // Dragging area covers the whole bar height
        val isOverMenuBar = mouseY[0] >= 0 && mouseY[0] <= barHeight

        if (isOverMenuBar && ImGui.isWindowHovered() && ImGui.isMouseClicked(0)) {
            if (!ImGui.isAnyItemActive() && !ImGui.isAnyItemHovered()) {
                windowController.startDrag(mouseX[0], mouseY[0])
            }
        }

        if (ImGui.isMouseDown(0)) {
            windowController.updateDrag(mouseX[0], mouseY[0])
        } else {
            windowController.stopDrag()
        }
    }

    private fun buildWindowControls(barHeight: Float) {
        val btnSize = 48f
        val totalW = btnSize * 3f

        // Calculate starting X to be exactly totalW from the right edge of the available space
        val currentX = ImGui.getCursorPosX()
        val availX = ImGui.getContentRegionAvailX()
        ImGui.setCursorPosX(currentX + availX - totalW)

        // Reset Y to 0 relative to the menu bar to ensure buttons are top-aligned
        ImGui.setCursorPosY(0f)

        // Remove all padding and spacing to ensure buttons are flush and uniform
        pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)
        pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)
        pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f) // Transparent base

        // Minimize
        if (ImGui.button(Icons.WINDOW_MINIMIZE, btnSize, btnSize)) {
            windowController.minimize()
        }

        // Maximize
        ImGui.setCursorPosY(0f)
        val maxRestoreIcon = if (windowController.isMaximized()) Icons.WINDOW_RESTORE else Icons.WINDOW_MAXIMIZE
        if (ImGui.button(maxRestoreIcon, btnSize, btnSize)) {
            windowController.toggleMaximize()
        }

        // Close - Red highlight
        ImGui.setCursorPosY(0f)
        pushStyleColor(ImGuiCol.ButtonHovered, 0.83f, 0.13f, 0.17f, 1f)
        pushStyleColor(ImGuiCol.ButtonActive, 0.93f, 0.23f, 0.27f, 1f)
        if (ImGui.button(Icons.WINDOW_CLOSE, btnSize, btnSize)) {
            windowController.close()
        }
        popStyleColor(2) // ButtonHovered, ButtonActive

        popStyleColor(1) // Button base
        popStyleVar(2) // FramePadding, ItemSpacing
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
