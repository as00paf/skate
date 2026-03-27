package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.DisplayService
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.windows.AssetBrowserWindow
import com.pafoid.skate.editor.windows.CommandHistoryWindow
import com.pafoid.skate.editor.windows.ConsoleWindow
import com.pafoid.skate.editor.windows.EnvironmentWindow
import com.pafoid.skate.editor.windows.GameViewWindow
import com.pafoid.skate.editor.windows.InputTestingWindow
import com.pafoid.skate.editor.windows.KeyBindingsWindow
import com.pafoid.skate.editor.windows.PhysicsTunerWindow
import com.pafoid.skate.editor.windows.ProfilerWindow
import com.pafoid.skate.editor.windows.PropertiesWindow
import com.pafoid.skate.editor.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.windows.SearchEverywhereWindow
import com.pafoid.skate.editor.windows.SettingsWindow
import com.pafoid.skate.editor.windows.SystemsWindow
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.game.level.LevelManager
import imgui.ImGui
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
import imgui.internal.ImGui.getContentRegionAvail
import imgui.internal.ImGui.getDrawData
import imgui.internal.ImGui.getID
import imgui.internal.ImGui.getIO
import imgui.internal.ImGui.getMainViewport
import imgui.internal.ImGui.image
import imgui.internal.ImGui.newFrame
import imgui.internal.ImGui.popStyleVar
import imgui.internal.ImGui.pushStyleVar
import imgui.internal.ImGui.render
import imgui.internal.ImGui.renderPlatformWindowsDefault
import imgui.internal.ImGui.setNextWindowPos
import imgui.internal.ImGui.setNextWindowSize
import imgui.internal.ImGui.setNextWindowViewport
import imgui.internal.ImGui.updatePlatformWindows
import imgui.type.ImBoolean
import imgui.type.ImInt
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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
    private val levelManager: LevelManager,
    private val resourceManager: ResourceManager,
    private val displayService: DisplayService
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
    private val settingsWindow = SettingsWindow(settingsManager, stringManager, displayService)
    private val keyBindingsWindow = KeyBindingsWindow(settingsManager, stringManager)
    private val searchEverywhereWindow = SearchEverywhereWindow()
    private val commandHistoryWindow = CommandHistoryWindow()
    private val statusBar = EditorStatusBar()
    private val editorInputHandler: EditorInputHandler by inject()

    private lateinit var menuBar: EditorMenuBar

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
        EditorWindow("window.systems", systemsWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.command_history", commandHistoryWindow, ImBoolean(false))
    )

    private var isViewportMaximized = false

    private lateinit var setFullscreen: (Boolean) -> Unit
    private lateinit var setVSync: (Boolean) -> Unit
    private lateinit var windowController: com.pafoid.skate.engine.core.WindowController

    /**
     * Flag to track if window decoration changed and needs ImGui viewport update.
     * Set by [onWindowDecorationChanged] and consumed in [endFrame].
     */
    private var needsDecorationUpdate = false

    private var layoutInitialized = false

    /**
     * Notifies ImGui that the window decoration state has changed.
     * Call this when toggling between maximized (undecorated) and restored (decorated) states
     * to ensure ImGui recalculates its work area to account for window decorations.
     */
    fun onWindowDecorationChanged() {
        // Defer the update until endFrame() to avoid ImGui assertion errors
        needsDecorationUpdate = true
    }

    /**
     * Gets the currently hovered game object from the GameViewWindow.
     */
    fun getHoveredGameObject(): com.pafoid.skate.engine.ecs.GameObject? {
        return gameViewWindow.getHoveredObject()
    }

    fun init(
        glfwWindow: Long,
        windowController: com.pafoid.skate.engine.core.WindowController,
        fullScreenCallback: (Boolean) -> Unit,
        vSyncCallback: (Boolean) -> Unit
    ) {
        this.glfwWindow = glfwWindow
        this.windowController = windowController
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

        // Initialize menu bar after editorWindows is populated
        menuBar = EditorMenuBar(
            stringManager = stringManager,
            levelManager = levelManager,
            undoRedoManager = undoRedoManager,
            clipboardService = clipboardService,
            sceneManager = sceneManager,
            settingsManager = settingsManager,
            resourceManager = resourceManager,
            keyBindingsWindow = keyBindingsWindow,
            settingsWindow = settingsWindow,
            searchEverywhereWindow = searchEverywhereWindow,
            editorWindows = editorWindows,
            glfwWindow = glfwWindow,
            windowController = windowController,
        )
    }

    private fun setupLayout(dockspaceId: Int) {
        if (layoutInitialized) return
        layoutInitialized = true

        val iniFile = File(Assets.Files.IMGUI)
        if (iniFile.exists()) return

        dockBuilderRemoveNode(dockspaceId)
        dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.PassthruCentralNode)
        dockBuilderSetNodeSize(
            dockspaceId,
            getMainViewport().sizeX,
            getMainViewport().sizeY
        )

        val mainBodyId = ImInt(0)
        // Split Left for Hierarchy
        val leftId = dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.18f, null, mainBodyId)
        // Split Right for Properties & Environment
        val rightId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Right, 0.22f, null, mainBodyId)
        // Split Bottom for Asset Browser, Console, Profiler
        val bottomId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Down, 0.28f, null, mainBodyId)

        // Set NoTabBar, NoWindowMenuButton, and NoCloseButton flags on the final central node to hide the "Game Viewport" tab entirely
        val centralNode = imgui.internal.ImGui.dockBuilderGetNode(mainBodyId.get())
        // NoTabBar (1 << 3), NoWindowMenuButton (1 << 12), NoCloseButton (1 << 13)
        val noTabBar = imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar
        val noWindowMenuButton = 1 shl 12
        val noCloseButton = 1 shl 13
        
        centralNode.setLocalFlags(noTabBar or noWindowMenuButton or noCloseButton)

        // Dock windows based on their logical function
        editorWindows.filter { it.showFlag.get() }.forEach { window ->
            val dockId = when (window.nameKey) {
                "window.hierarchy" -> leftId
                "window.properties", "window.environment", "window.systems" -> rightId
                "window.asset_browser", "window.console", "window.profiler", "window.physics_tuner" -> bottomId
                "window.game_viewport" -> mainBodyId.get()
                else -> mainBodyId.get()
            }
            dockBuilderDockWindow(stringManager.getString(window.nameKey), dockId)
        }

        dockBuilderFinish(dockspaceId)
    }

    fun update(dt: Float) {
        val currentScene = sceneManager.currentScene ?: return

        // Handle Ctrl+P BEFORE ImGui processes input (so it works even when typing)
        val ctrlDown = inputProvider.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || inputProvider.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)
        if (ctrlDown && inputProvider.keyBeginPress(GLFW.GLFW_KEY_P)) {
            searchEverywhereWindow.open()
        }

        if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_F12)) {
            isViewportMaximized = !isViewportMaximized
        }

        startFrame()

        // Handle other editor input (copy, cut, paste, etc.)
        editorInputHandler.update(currentScene)

        if (isViewportMaximized) {
            setNextWindowPos(getMainViewport().workPosX, getMainViewport().workPosY, ImGuiCond.Always)
            setNextWindowSize(getMainViewport().workSizeX, getMainViewport().workSizeY, ImGuiCond.Always)
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
            statusBar.render(currentScene)
            currentScene.imguiScene()

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
            searchEverywhereWindow.imgui(null)
            commandHistoryWindow.imgui(null)
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
        } else if (needsDecorationUpdate) {
            // If viewports are not enabled, we still need to update platform windows
            // when decoration state changes to recalculate work area
            val backupWindowPtr = GLFW.glfwGetCurrentContext()
            updatePlatformWindows()
            GLFW.glfwMakeContextCurrent(backupWindowPtr)
        }

        needsDecorationUpdate = false
    }

    private fun setupDockSpace(currentScene: Scene) {
        var windowFlags = ImGuiWindowFlags.MenuBar or ImGuiWindowFlags.NoDocking

        val viewport = getMainViewport()
        val statusBarHeight = 30f // Height for EditorStatusBar

        setNextWindowPos(viewport.workPosX, viewport.workPosY, ImGuiCond.Always)
        setNextWindowSize(viewport.workSizeX, viewport.workSizeY - statusBarHeight, ImGuiCond.Always)
        setNextWindowViewport(viewport.id)

        windowFlags = windowFlags or (ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus)

        // Push large FramePadding specifically to give the Menu Bar more vertical space
        pushStyleVar(ImGuiStyleVar.FramePadding, 12f, 22f)
        pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f)
        pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f)

        begin(stringManager.getString("lbl.editor_title"), ImBoolean(true), windowFlags)

        popStyleVar(4) // Pop FramePadding, WindowPadding, WindowRounding, WindowBorderSize

        setupLayout(getID("DockSpace"))
        dockSpace(getID("DockSpace"))

        // Render menu bar
        menuBar.render(currentScene)

        end()
    }

    fun destroy() {
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()
        destroyContext()
    }
}
