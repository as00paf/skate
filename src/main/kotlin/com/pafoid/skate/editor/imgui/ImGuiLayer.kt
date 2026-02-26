package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.CreateGameObjectCommand
import com.pafoid.skate.editor.systems.DeleteGameObjectCommand
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.windows.AssetBrowserWindow
import com.pafoid.skate.editor.windows.ConsoleWindow
import com.pafoid.skate.editor.windows.EnvironmentWindow
import com.pafoid.skate.editor.windows.GameViewWindow
import com.pafoid.skate.editor.windows.InputTestingWindow
import com.pafoid.skate.editor.windows.KeyBindingsWindow
import com.pafoid.skate.editor.windows.PhysicsTunerWindow
import com.pafoid.skate.editor.windows.ProfilerWindow
import com.pafoid.skate.editor.windows.PropertiesWindow
import com.pafoid.skate.editor.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.windows.SettingsWindow
import com.pafoid.skate.editor.windows.SystemsWindow
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.UnitSystem
import com.pafoid.skate.game.level.LevelManager
import imgui.ImVec2
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiDockNodeFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.beginMenuBar
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.createContext
import imgui.internal.ImGui.destroyContext
import imgui.internal.ImGui.dockBuilderAddNode
import imgui.internal.ImGui.dockBuilderDockWindow
import imgui.internal.ImGui.dockBuilderFinish
import imgui.internal.ImGui.dockBuilderRemoveNode
import imgui.internal.ImGui.dockBuilderSetNodeSize
import imgui.internal.ImGui.dockBuilderSplitNode
import imgui.internal.ImGui.dockSpace
import imgui.internal.ImGui.end
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.endMenuBar
import imgui.internal.ImGui.getContentRegionAvail
import imgui.internal.ImGui.getDrawData
import imgui.internal.ImGui.getID
import imgui.internal.ImGui.getIO
import imgui.internal.ImGui.getMainViewport
import imgui.internal.ImGui.image
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.newFrame
import imgui.internal.ImGui.popStyleVar
import imgui.internal.ImGui.pushStyleVar
import imgui.internal.ImGui.render
import imgui.internal.ImGui.renderPlatformWindowsDefault
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.setNextWindowPos
import imgui.internal.ImGui.setNextWindowSize
import imgui.internal.ImGui.setNextWindowViewport
import imgui.internal.ImGui.sliderFloat
import imgui.internal.ImGui.updatePlatformWindows
import imgui.type.ImBoolean
import imgui.type.ImInt
import org.koin.core.component.KoinComponent
import org.lwjgl.glfw.GLFW
import java.io.File

class ImGuiLayer(
    private val inputProvider: IInputProvider,
    private val settingsManager: SettingsManager,
    private val sceneManager: SceneManager,
    private val clipboardService: ClipboardService,
    private val stringManager: StringManager,
    private val undoRedoManager: UndoRedoManager,
    private val renderer: Renderer,
    private val levelManager: LevelManager
): KoinComponent {

    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"
    private var glfwWindow: Long = 0

    // Window instances
    private val hierarchyWindow = SceneHierarchyWindow()
    private val propertiesWindow = PropertiesWindow()
    private val gameViewWindow = GameViewWindow()
    private val assetBrowser = AssetBrowserWindow()
    private val environmentWindow = EnvironmentWindow()
    private val profilerWindow = ProfilerWindow()
    private val consoleWindow = ConsoleWindow()
    private val physicsTunerWindow = PhysicsTunerWindow()
    private val inputTestingWindow = InputTestingWindow(inputProvider, settingsManager, stringManager)
    private val systemsWindow = SystemsWindow()
    private val settingsWindow = SettingsWindow(settingsManager, stringManager)
    private val keyBindingsWindow = KeyBindingsWindow(settingsManager, stringManager)

    /**
     * Data class combining window instance with its visibility flag and metadata.
     */
    private data class EditorWindow(
        val nameKey: String,           // Localization key for window title
        val instance: Any,             // Window instance
        val showFlag: ImBoolean,       // Visibility toggle flag
        val requiresScene: Boolean = false  // Whether imgui() needs Scene parameter
    )

    /**
     * Registry of all dockable editor windows.
     * Centralizes window management for rendering, menus, and dock layout.
     */
    private val editorWindows = listOf(
        EditorWindow("window.hierarchy", hierarchyWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.properties", propertiesWindow, ImBoolean(true)),
        EditorWindow("window.game_viewport", gameViewWindow, ImBoolean(true)),
        EditorWindow("window.asset_browser", assetBrowser, ImBoolean(true)),
        EditorWindow("window.environment", environmentWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.profiler", profilerWindow, ImBoolean(true)),
        EditorWindow("window.console", consoleWindow, ImBoolean(true)),
        EditorWindow("window.physics_tuner", physicsTunerWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.input_testing", inputTestingWindow, ImBoolean(false)),
        EditorWindow("window.systems", systemsWindow, ImBoolean(false), requiresScene = true)
    )

    private var isViewportMaximized = false

    private lateinit var setFullscreen: (Boolean) -> Unit
    private lateinit var setVSync: (Boolean) -> Unit

    /**
     * Gets the currently hovered game object from the GameViewWindow.
     */
    fun getHoveredGameObject(): com.pafoid.skate.engine.ecs.GameObject? {
        return gameViewWindow.getHoveredObject()
    }

    fun init(glfwWindow: Long, fullScreenCallback:(Boolean)->Unit, vSyncCallback:(Boolean)->Unit) {
        this.glfwWindow = glfwWindow
        this.setFullscreen = fullScreenCallback
        this.setVSync = vSyncCallback

        createContext()

        with(getIO()) {
            iniFilename = Assets.Files.IMGUI
            backendPlatformName = "imgui_java_impl_glfw"
            addConfigFlags(ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.ViewportsEnable)
            loadFonts(Assets.Fonts.fontsFile)
        }

        imGuiGlfw.init(glfwWindow, true)
        imGuiGl3.init(glslVersion)

        ImGuiStyleManager.setupStyle()
    }

    private fun setupLayout(dockspaceId: Int) {
        val iniFile = File(Assets.Files.IMGUI)
        if (iniFile.exists()) return

        dockBuilderRemoveNode(dockspaceId)
        dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.None)
        dockBuilderSetNodeSize(
            dockspaceId,
            getMainViewport().sizeX,
            getMainViewport().sizeY
        )

        val mainBodyId = ImInt(0)
        val leftId = dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.2f, null, mainBodyId)
        val rightId =
            dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Right, 0.25f, null, mainBodyId)
        val bottomId =
            dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Down, 0.25f, null, mainBodyId)

        // Dock windows based on their default visibility
        editorWindows.filter { it.showFlag.get() }.forEach { window ->
            val dockId = when (window.nameKey) {
                "window.hierarchy", "window.asset_browser", "window.properties" -> leftId
                "window.game_viewport" -> mainBodyId.get()
                "window.console", "window.profiler", "window.environment", "window.physics_tuner" -> bottomId
                else -> mainBodyId.get() // Default to main area for unknown windows
            }
            dockBuilderDockWindow(stringManager.getString(window.nameKey), dockId)
        }

        dockBuilderFinish(dockspaceId)
    }

    fun update(dt: Float, currentScene: Scene) {
        if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_F12)) {
            isViewportMaximized = !isViewportMaximized
        }

        startFrame()

        if (isViewportMaximized) {
            setNextWindowPos(getMainViewport().workPosX, getMainViewport().workPosY)
            setNextWindowSize(getMainViewport().workSizeX, getMainViewport().workSizeY)
            pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            begin(
                stringManager.getString("window.game_viewport") + " Maximized",
                ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoDecoration
            )

            val windowSize = ImVec2()
            getContentRegionAvail(windowSize)

            val texId = renderer.frameBuffer.getTextureId()
            image(texId.toLong(), windowSize.x, windowSize.y, 0f, 1f, 1f, 0f)

            end()
            popStyleVar()
        } else {
            setupDockSpace(currentScene)
            currentScene.imgui()

            // Render all visible editor windows
            editorWindows.forEach { window ->
                if (window.showFlag.get()) {
                    when {
                        window.requiresScene -> (window.instance as IWindowWithScene).imgui(currentScene)
                        else -> (window.instance as IWindow).imgui()
                    }
                }
            }
            settingsWindow.render()
            keyBindingsWindow.render()
        }

        endFrame()
    }

    fun startFrame() {
        imGuiGlfw.newFrame()
        imGuiGl3.newFrame()
        newFrame()
    }

    fun endFrame() {
        render()
        imGuiGl3.renderDrawData(getDrawData())

        if (getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            val backupWindowPtr = GLFW.glfwGetCurrentContext()
            updatePlatformWindows()
            renderPlatformWindowsDefault()
            GLFW.glfwMakeContextCurrent(backupWindowPtr)
        }
    }

    private fun setupDockSpace(currentScene: Scene) {
        var windowFlags = ImGuiWindowFlags.MenuBar or ImGuiWindowFlags.NoDocking

        val viewport = getMainViewport()
        setNextWindowPos(viewport.workPosX, viewport.workPosY)
        setNextWindowSize(viewport.workSizeX, viewport.workSizeY)
        setNextWindowViewport(viewport.id)
        setNextWindowPos(0.0f, 0.0f, ImGuiCond.Always)

        windowFlags = windowFlags or (ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus)

        pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f)
        pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f)
        begin(stringManager.getString("lbl.editor_title"), ImBoolean(true), windowFlags)
        popStyleVar(2)

        dockSpace(getID("DockSpace"))
        setupLayout(getID("DockSpace"))

        if (beginMenuBar()) {
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
            if (beginMenu(stringManager.getString("menu.settings"))) {
                val settings = settingsManager.settings

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
                val languages = arrayOf("en", "fr") // Add more languages here
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
                    settingsManager.setLocale(newLang) // This will also reload StringManager
                    settingsManager.save()
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
            endMenuBar()
        }

        end()
    }

    fun destroy() {
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()
        destroyContext()
    }
}
