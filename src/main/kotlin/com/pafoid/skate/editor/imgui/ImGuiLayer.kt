package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.systems.WindowRegistry
import com.pafoid.skate.editor.ui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.menus.WindowControlsRenderer
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.WindowController
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.render.renderer.Renderer
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
import org.lwjgl.glfw.GLFW.GLFW_DECORATED
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import java.io.File

class ImGuiLayer(
    private val inputProvider: IInputProvider,
    private val settingsManager: SettingsManager,
    private val sceneManager: SceneManager,
    private val clipboardService: ClipboardService,
    private val stringManager: StringManager,
    private val undoRedoManager: UndoRedoManager,
    private val renderer: Renderer,
    private val resourceManager: ResourceManager,
    private val windowRegistry: WindowRegistry,
    private val gameObjectManager: GameObjectManager,
): KoinComponent {

    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"

    private val eventSystem: EventSystem by inject()
    private val projectManager: ProjectManager by inject()
    private val statusBar = EditorStatusBar()
    private lateinit var menuBar: EditorMenuBar

    private val tempVec2 = ImVec2()

    private var isViewportMaximized = false
    private var hadProjectLastFrame = false
    private var needsWizardReset = false
    private var hasAttemptedAutoLoad = false

    private lateinit var windowController: WindowController

    private var needsDecorationUpdate = false
    private var layoutInitialized = false

    fun markWizardResetNeeded() {
        needsWizardReset = true
    }

    fun init(
        windowController: WindowController
    ) {
        this.windowController = windowController
        val glfwWindow = windowController.glfwWindow

        GLFW.glfwSetWindowMaximizeCallback(glfwWindow) { _, maximized ->
            if (windowController.isFixingMaximize) return@glfwSetWindowMaximizeCallback

            windowController.setLogicallyMaximized(maximized)
            if (maximized) {
                GLFW.glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_FALSE)
                windowController.isFixingMaximize = true
            } else {
                GLFW.glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_TRUE)
                onWindowDecorationChanged()
            }
        }

        createContext()

        with(getIO()) {
            iniFilename = Assets.Files.IMGUI
            backendPlatformName = "imgui_java_impl_glfw"
            addConfigFlags(ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.ViewportsEnable)
            loadFonts(Assets.Fonts.fontsFile)
        }

        imGuiGlfw.init(windowController.glfwWindow, true)
        imGuiGl3.init(glslVersion)

        ImGuiStyleManager.setupStyle()

        val editorSettingsShowFlag = windowRegistry.windows.find { it.nameKey == "window.editor_settings" }?.showFlag ?: ImBoolean(false)
        val projectSettingsShowFlag = windowRegistry.windows.find { it.nameKey == "window.project_settings" }?.showFlag ?: ImBoolean(false)

        menuBar = EditorMenuBar(
            fileMenu = FileMenuBuilder(
                stringManager,
                eventSystem,
                sceneManager,
                windowController.glfwWindow
            ),
            editMenu = EditMenuBuilder(
                stringManager,
                undoRedoManager,
                clipboardService,
                sceneManager,
                eventSystem
            ),
            settingsMenu = SettingsMenuBuilder(
                stringManager, settingsManager,
                keyBindingsShowFlag = windowRegistry.windows.find { it.nameKey == "window.keybindings" }?.showFlag ?: ImBoolean(false),
                settingsShowFlag = editorSettingsShowFlag
            ),
            viewMenu = ViewMenuBuilder(stringManager, windowRegistry.windows),
            windowControls = WindowControlsRenderer(
                windowRegistry.searchEverywhereWindow,
                windowController,
                stringManager,
                editorSettingsShowFlag,
                projectSettingsShowFlag
            ),
            stringManager = stringManager,
            resourceManager = resourceManager,
            projectManager = projectManager,
            eventSystem = eventSystem,
            projectSwitcher = windowRegistry.projectSwitcherDialog,
            windowController = windowController,
            projectWizard = windowRegistry.projectWizardWindow.wizard,
            imguiLayer = this
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

        setupDockSpace(currentScene)

        if (isViewportMaximized) {
            setNextWindowPos(getMainViewport().workPosX, getMainViewport().workPosY, ImGuiCond.Always)
            setNextWindowSize(getMainViewport().workSizeX, getMainViewport().workSizeY, ImGuiCond.Always)
            pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            begin(
                stringManager.getString(
                    "window.game_viewport.maximized",
                    stringManager.getString("window.game_viewport")
                ),
                ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoDecoration
            )

            getContentRegionAvail(tempVec2)

            val texId = renderer.frameBuffer.getTextureId()
            image(texId.toLong(), tempVec2.x, tempVec2.y, 0f, 1f, 1f, 0f)

            end()
            popStyleVar()
        } else if (projectManager.hasProject()) {
            windowRegistry.windows.forEach { window ->
                if (window.showFlag.get()) {
                    // Re-read current scene in case it was switched during the frame (e.g., tab bar click)
                    val activeScene = sceneManager.currentScene ?: return
                    when {
                        window.requiresScene -> (window.instance as? IWindowWithScene)?.imgui(activeScene)
                        else -> (window.instance as? IWindow)?.imgui(window.showFlag)
                    }
                }
            }

            windowRegistry.searchEverywhereWindow.imgui(null)
            windowRegistry.projectSwitcherDialog.render()
        }

        statusBar.render(currentScene)

        if (needsWizardReset) {
            windowRegistry.projectWizardWindow.wizard.resetForNewProject()
            needsWizardReset = false
        }

        processProjectStartupFlow()

        windowRegistry.projectWizardWindow.imgui(null)

        endFrame()
    }

    internal fun processProjectStartupFlow() {
        if (!hasAttemptedAutoLoad && !projectManager.hasProject()) {
            hasAttemptedAutoLoad = true
            eventSystem.publish(ProjectEvent.LoadLastProjectRequested)
        }

        // Detect when a project was just closed — hide all project windows
        if (hadProjectLastFrame && !projectManager.hasProject()) {
            windowRegistry.hideAllWindows()
        }

        // Project was just opened — show default windows and dismiss wizard
        if (!hadProjectLastFrame && projectManager.hasProject()) {
            windowRegistry.showDefaultWindows()
            if (windowRegistry.projectWizardWindow.wizard.isOpen.get()) {
                windowRegistry.projectWizardWindow.wizard.dismiss()
            }
        }
        hadProjectLastFrame = projectManager.hasProject()

        if (!projectManager.hasProject() && !windowRegistry.projectWizardWindow.wizard.isOpen.get() && !windowRegistry.projectWizardWindow.wizard.userDismissed) {
            windowRegistry.projectWizardWindow.wizard.open()
        }
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

    fun destroy() {
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()
        destroyContext()
    }
}
