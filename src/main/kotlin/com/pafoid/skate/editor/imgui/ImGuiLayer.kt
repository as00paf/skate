package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.WindowRegistry
import com.pafoid.skate.editor.ui.imgui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.WindowControlsRenderer
import com.pafoid.skate.editor.windows.SearchEverywhereWindow
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.WindowController
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.game.level.LevelManager
import com.pafoid.skate.game.project.ProjectManager
import imgui.ImVec2
import imgui.ImGui
import imgui.flag.ImGuiCol
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
import imgui.internal.ImGui.popStyleColor
import imgui.internal.ImGui.popStyleVar
import imgui.internal.ImGui.pushStyleColor
import imgui.internal.ImGui.pushStyleVar
import imgui.internal.ImGui.render
import imgui.internal.ImGui.renderPlatformWindowsDefault
import imgui.internal.ImGui.setNextWindowPos
import imgui.internal.ImGui.setNextWindowSize
import imgui.internal.ImGui.setNextWindowViewport
import imgui.internal.ImGui.text
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
    private val windowRegistry: WindowRegistry,
): KoinComponent {

    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"
    private var glfwWindow: Long = 0

    private val eventSystem: EventSystem by inject()
    private val editorInputHandler: EditorInputHandler by inject()
    private val projectManager: ProjectManager by inject()
    private val statusBar = EditorStatusBar()
    private lateinit var menuBar: EditorMenuBar
    
    // Reusable buffer to avoid per-frame allocations
    private val tempVec2 = ImVec2()

    private var isViewportMaximized = false

    private lateinit var setFullscreen: (Boolean) -> Unit
    private lateinit var setVSync: (Boolean) -> Unit
    private lateinit var windowController: WindowController

    private var needsDecorationUpdate = false
    private var layoutInitialized = false

    fun init(
        glfwWindow: Long,
        windowController: WindowController,
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

        menuBar = EditorMenuBar(
            fileMenu = FileMenuBuilder(stringManager, levelManager, sceneManager, glfwWindow),
            editMenu = EditMenuBuilder(stringManager, undoRedoManager, clipboardService, sceneManager, eventSystem),
            settingsMenu = SettingsMenuBuilder(
                stringManager, settingsManager,
                keyBindingsShowFlag = windowRegistry.windows.find { it.nameKey == "window.keybindings" }?.showFlag ?: ImBoolean(false),
                settingsShowFlag = windowRegistry.windows.find { it.nameKey == "window.settings" }?.showFlag ?: ImBoolean(false)
            ),
            viewMenu = ViewMenuBuilder(stringManager, windowRegistry.windows),
            windowControls = WindowControlsRenderer(windowRegistry.searchEverywhereWindow, windowController),
            stringManager = stringManager,
            resourceManager = resourceManager,
            projectManager = projectManager,
            projectSwitcher = windowRegistry.projectSwitcherDialog,
            windowController = windowController,
            projectWizard = windowRegistry.projectWizardWindow.wizard
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
        val leftId = dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.18f, null, mainBodyId)
        val rightId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Right, 0.22f, null, mainBodyId)
        val bottomId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Down, 0.28f, null, mainBodyId)

        val centralNode = imgui.internal.ImGui.dockBuilderGetNode(mainBodyId.get())
        val noTabBar = imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar
        val noWindowMenuButton = 1 shl 12
        val noCloseButton = 1 shl 13
        
        centralNode.setLocalFlags(noTabBar or noWindowMenuButton or noCloseButton)

        windowRegistry.windows.filter { it.showFlag.get() }.forEach { window ->
            val dockId = when (window.nameKey) {
                "window.hierarchy", "window.properties", "window.systems", "window.asset_browser", "window.command_history", "window.render_graph" -> leftId
                "window.console", "window.profiler", "window.physics_tuner", "window.environment" -> bottomId
                "window.game_viewport" -> mainBodyId.get()
                else -> mainBodyId.get()
            }
            dockBuilderDockWindow(stringManager.getString(window.nameKey), dockId)
        }

        dockBuilderFinish(dockspaceId)
    }

    fun update(dt: Float) {
        val currentScene = sceneManager.currentScene ?: return
        val ctrlDown = inputProvider.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || inputProvider.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)

        if (ctrlDown && inputProvider.keyBeginPress(GLFW.GLFW_KEY_P)) {
            windowRegistry.searchEverywhereWindow.open()
        }

        if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_F12)) {
            isViewportMaximized = !isViewportMaximized
        }

        startFrame()

        editorInputHandler.update(currentScene)

        // Setup dockspace first (always needed for ImGui context)
        setupDockSpace(currentScene)

        // Try to auto-load the last project before showing the wizard
        if (!projectManager.hasProject()) {
            projectManager.loadLastProject()
        }

        // Show project wizard if no project is loaded (overlay on top of editor)
        if (!projectManager.hasProject()) {
            windowRegistry.projectWizardWindow.imgui(null)
        }

        if (isViewportMaximized) {
            setNextWindowPos(getMainViewport().workPosX, getMainViewport().workPosY, ImGuiCond.Always)
            setNextWindowSize(getMainViewport().workSizeX, getMainViewport().workSizeY, ImGuiCond.Always)
            pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            begin(
                stringManager.getString("window.game_viewport") + " Maximized",
                ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoDecoration
            )

            getContentRegionAvail(tempVec2)

            val texId = renderer.frameBuffer.getTextureId()
            image(texId.toLong(), tempVec2.x, tempVec2.y, 0f, 1f, 1f, 0f)

            end()
            popStyleVar()
        } else if (projectManager.hasProject()) {
            // Only render project-dependent UI when a project is loaded
            currentScene.imguiScene()

            // Render all dockable windows through the registry
            windowRegistry.windows.forEach { window ->
                if (window.showFlag.get()) {
                    when {
                        window.requiresScene -> (window.instance as? IWindowWithScene)?.imgui(currentScene)
                        else -> (window.instance as? IWindow)?.imgui()
                    }
                }
            }

            // Render modal/overlay windows that aren't in the dockable list
            windowRegistry.searchEverywhereWindow.imgui(null)
            windowRegistry.projectSwitcherDialog.render()
        }

        statusBar.render(currentScene)

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
            val backupWindowPtr = GLFW.glfwGetCurrentContext()
            updatePlatformWindows()
            GLFW.glfwMakeContextCurrent(backupWindowPtr)
        }

        needsDecorationUpdate = false
    }

    private fun setupDockSpace(currentScene: Scene) {
        var windowFlags = ImGuiWindowFlags.MenuBar or ImGuiWindowFlags.NoDocking

        val viewport = getMainViewport()
        val statusBarHeight = com.pafoid.skate.editor.imgui.data.UiConstants.STATUS_BAR_HEIGHT

        setNextWindowPos(viewport.workPosX, viewport.workPosY, ImGuiCond.Always)
        setNextWindowSize(viewport.workSizeX, viewport.workSizeY - statusBarHeight, ImGuiCond.Always)
        setNextWindowViewport(viewport.id)

        windowFlags = windowFlags or (ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus)

        pushStyleVar(ImGuiStyleVar.FramePadding, 12f, 22f)
        pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f)
        pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f)

        begin(stringManager.getString("lbl.editor_title"), ImBoolean(true), windowFlags)

        popStyleVar(4)

        setupLayout(getID("DockSpace"))
        dockSpace(getID("DockSpace"))

        menuBar.render(currentScene)

        end()
    }

    fun onWindowDecorationChanged() {
        needsDecorationUpdate = true
    }

    fun getHoveredGameObject(): com.pafoid.skate.engine.ecs.GameObject? {
        return windowRegistry.gameViewWindow.getHoveredObject()
    }

    fun destroy() {
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()
        destroyContext()
    }
}
