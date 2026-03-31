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
import com.pafoid.skate.editor.windows.SearchEverywhereWindow
import com.pafoid.skate.editor.windows.SettingsWindow
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.WindowController
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SceneOpened
import com.pafoid.skate.engine.events.SelectionCleared
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
    private val searchEverywhereWindow: SearchEverywhereWindow,
    private val editorWindows: List<EditorWindow>,
    private val glfwWindow: Long,
    private val windowController: WindowController,
    private val eventSystem: EventSystem
) {
    private var appIconTexId = -1
    private val projectIcon = Icons.CUBE
    private val projectName = "Skate Project"

    init {
        appIconTexId = resourceManager.loadTextureSync(Assets.Textures.APP_ICON).texId
    }

    fun render(currentScene: Scene) {
        if (beginMenuBar()) {
            val barHeight = 48f

            renderAppIcon(barHeight)
            renderHamburgerMenu(currentScene, barHeight)
            renderProjectInfo(barHeight)
            buildWindowControls()

            endMenuBar()
        }
    }

    private fun renderAppIcon(barHeight: Float) {
        if (appIconTexId != -1) {
            val iconSize = 32f

            ImGui.setCursorPosY((barHeight - iconSize) / 2f)
            image(appIconTexId.toLong(), iconSize, iconSize)
        }
    }

    private fun renderHamburgerMenu(currentScene: Scene, barHeight: Float) {
        val btnSize = 30f
        val offsetY = (barHeight - btnSize) / 2f
        ImGui.setCursorPosY(offsetY)

        if (ImGui.button(Icons.MENU, btnSize, btnSize)) {
            ImGui.openPopup("main_hamburger_menu")
        }

        if (ImGui.beginPopup("main_hamburger_menu")) {
            buildFileMenu(currentScene)
            buildEditMenu(currentScene)
            buildSettingsMenu()
            buildViewMenu()
            ImGui.separator()
            if (ImGui.menuItem(stringManager.getString("menu.file.quit"))) {
                windowController.close()
            }
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

    private fun buildWindowControls() {
        val btnSize = 40f
        val totalW = btnSize * 4f

        val currentX = ImGui.getCursorPosX()
        val availX = ImGui.getContentRegionAvailX()
        ImGui.setCursorPosX(currentX + availX - totalW)

        pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)
        pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)
        pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f) // Transparent base

        if (ImGui.button("${Icons.SEARCH}", btnSize, btnSize)) {
            searchEverywhereWindow.open()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Search Everywhere (Ctrl+P)")
        }

        ImGui.sameLine(0f, 0f)
        if (ImGui.button(Icons.WINDOW_MINIMIZE, btnSize, btnSize)) {
            windowController.minimize()
        }

        val maxRestoreIcon = if (windowController.isMaximized()) Icons.WINDOW_RESTORE else Icons.WINDOW_MAXIMIZE
        if (ImGui.button(maxRestoreIcon, btnSize, btnSize)) {
            windowController.toggleMaximize()
        }

        pushStyleColor(ImGuiCol.ButtonHovered, 0.83f, 0.13f, 0.17f, 1f)
        pushStyleColor(ImGuiCol.ButtonActive, 0.93f, 0.23f, 0.27f, 1f)
        if (ImGui.button(Icons.WINDOW_CLOSE, btnSize, btnSize)) {
            windowController.close()
        }
        popStyleColor(2)

        popStyleColor(1)
        popStyleVar(2)
    }

    private fun buildFileMenu(currentScene: Scene) {
        if (beginMenu(stringManager.getString("menu.file"))) {
            if (menuItem("${Icons.PLUS} New Scene", "Ctrl+N")) {
                com.pafoid.skate.engine.utils.JobSystem.runOnMain {
                    val initializer = com.pafoid.skate.editor.LevelEditorSceneInitializer()
                    val newScene = Scene("New Scene", initializer)
                    newScene.init()
                    sceneManager.openScene(newScene)
                }
            }
            if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save")}", "Ctrl+S")) {
                levelManager.save(currentScene)
            }
            if (menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_as")}")) {
                levelManager.saveAs(currentScene)
            }
            if (menuItem("${Icons.FOLDER_OPEN} ${stringManager.getString("menu.file.open")}", "Ctrl+O")) {
                com.pafoid.skate.engine.utils.JobSystem.runOnMain {
                    val initializer = com.pafoid.skate.editor.LevelEditorSceneInitializer()
                    val newScene = Scene("Loaded Scene", initializer)
                    newScene.init()
                    sceneManager.openScene(newScene)
                    levelManager.open(newScene)
                }
            }
            separator()
            if (menuItem("${Icons.TRASH} ${stringManager.getString("menu.file.quit")}")) {
                GLFW.glfwSetWindowShouldClose(glfwWindow, true)
            }
            endMenu()
        }
    }

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
                    eventSystem.publish(SelectionCleared)
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

    private fun buildSettingsMenu() {
        if (beginMenu(stringManager.getString("menu.settings"))) {
            val engineSettings = settingsManager.engine

            val editorSettings = engineSettings.editor
            val overlaySize = floatArrayOf(editorSettings.gamepadOverlaySize)
            if (sliderFloat(
                    stringManager.getString("menu.settings.gamepad_overlay_size"),
                    overlaySize,
                    0.05f,
                    0.5f
                )
            ) {
                editorSettings.gamepadOverlaySize = overlaySize[0]
                settingsManager.saveEngine()
            }

            val showOverlay = ImBoolean(editorSettings.showGamepadOverlay)
            if (checkbox(stringManager.getString("menu.settings.show_gamepad_overlay"), showOverlay)) {
                editorSettings.showGamepadOverlay = showOverlay.get()
                settingsManager.saveEngine()
            }

            separator()

            val unitSystems = UnitSystem.entries.toTypedArray()
            val currentUnitIdx = ImInt(editorSettings.unitSystem.ordinal)
            if (combo(
                    stringManager.getString("menu.settings.unit_system"),
                    currentUnitIdx,
                    unitSystems.map { it.name }.toTypedArray()
                )
            ) {
                editorSettings.unitSystem = unitSystems[currentUnitIdx.get()]
                settingsManager.saveEngine()
            }

            separator()

            val languages = arrayOf("en", "fr")
            val currentLangIdx = ImInt(languages.indexOf(editorSettings.language))
            if (combo(
                    stringManager.getString("menu.settings.language"),
                    currentLangIdx,
                    languages,
                    languages.size
                )
            ) {
                val newLang = languages[currentLangIdx.get()]
                editorSettings.language = newLang
                settingsManager.setLocale(newLang)
                settingsManager.saveEngine()
            }

            separator()

            if (menuItem(stringManager.getString("menu.settings.keybindings"))) {
                keyBindingsWindow.isOpen = true
            }
            if (menuItem(stringManager.getString("menu.settings.settings"))) {
                settingsWindow.isOpen = true
            }

            endMenu()
        }
    }

    private fun buildViewMenu() {
        if (beginMenu(stringManager.getString("menu.view"))) {
            if (beginMenu(stringManager.getString("menu.view.windows"))) {
                editorWindows.forEach { window ->
                    checkbox(stringManager.getString(window.nameKey), window.showFlag)
                }
                endMenu()
            }
            endMenu()
        }
    }
}
