package com.pafoid.skate.editor.imgui

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.imgui.data.UiConstants
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.WindowController
import com.pafoid.skate.engine.ecs.Scene
import imgui.ImVec2
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiDockNodeFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.internal.ImGui
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.createContext
import imgui.internal.ImGui.destroyContext
import imgui.internal.ImGui.dockBuilderAddNode
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
import org.lwjgl.glfw.GLFW
import java.io.File

class ImGuiLayer(
    private val engine: Engine,
    editor: Editor,
    private val stringManager: StringManager,
    projectManager: ProjectManager,
    settingsManager: EditorSettingsManager,
    private val gizmoSystem: GizmoSystem,
) {
    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"

    private val tempVec2 = ImVec2()

    var isViewportMaximized = false

    private var layoutInitialized = false

    private val windowRegistry = WindowRegistry(engine, editor, projectManager, settingsManager, stringManager)

    private val menuBar: EditorMenuBar = EditorMenuBar(
        stringManager,
        engine.assetsManager,
        projectManager,
        settingsManager,
        engine.sceneManager,
        windowRegistry,
        engine.eventSystem
    )
    private val statusBar = EditorStatusBar(stringManager)
    private val windowManager = WindowManager(stringManager, windowRegistry, engine.eventSystem)

    fun init(windowController: WindowController) {
        createContext()

        with(getIO()) {
            iniFilename = Assets.Files.IMGUI
            backendPlatformName = "imgui_java_impl_glfw"
            addConfigFlags(ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.ViewportsEnable)
            val fontsByteArray =
                this.javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)?.readAllBytes()
            fontsByteArray?.let { loadFonts(it) }
        }

        imGuiGlfw.init(windowController.glfwWindow, true)
        imGuiGl3.init(glslVersion)

        ImGuiStyleManager.setupStyle()

        windowManager.init()
        windowController.onToggleMaximize = { maximized -> menuBar.setMaximized(maximized) }
        engine.eventSystem.subscribe<EditorEvent.Exit> { windowController.close() }
        engine.eventSystem.subscribe<EditorEvent.Minimize> { windowController.minimize() }
        engine.eventSystem.subscribe<EditorEvent.ToggleMaximize> { windowController.toggleMaximize() }
    }

    private fun setupLayout(dockspaceId: Int) {
        if (layoutInitialized) return
        layoutInitialized = true

        val iniFile = File(Assets.Files.IMGUI)
        if (iniFile.exists()) return

        dockBuilderRemoveNode(dockspaceId)
        dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.PassthruCentralNode)
        dockBuilderSetNodeSize(dockspaceId, getMainViewport().sizeX, getMainViewport().sizeY)

        val mainBodyId = ImInt(0)
        val leftId = dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.18f, null, mainBodyId)
        val rightId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Right, 0.22f, null, mainBodyId)
        val bottomId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Down, 0.28f, null, mainBodyId)

        val centralNode = ImGui.dockBuilderGetNode(mainBodyId.get())
        val noTabBar = imgui.internal.flag.ImGuiDockNodeFlags.NoTabBar
        val noWindowMenuButton = 1 shl 12
        val noCloseButton = 1 shl 13

        centralNode.setLocalFlags(noTabBar or noWindowMenuButton or noCloseButton)

        windowManager.dockWindows(mainBodyId, leftId, rightId, bottomId)

        dockBuilderFinish(dockspaceId)
    }

    fun update(dt: Float) {
        val currentScene = engine.sceneManager.currentScene

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

            val texId = engine.renderer.frameBuffer.getTextureId()
            image(texId.toLong(), tempVec2.x, tempVec2.y, 0f, 1f, 1f, 0f)

            end()
            popStyleVar()// TODO: wtf
        } else {
            currentScene?.let { gizmoSystem.update(dt, it) }
            statusBar.render(currentScene)
            windowManager.update(currentScene)
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

    private fun setupDockSpace(currentScene: Scene?) {
        var windowFlags = ImGuiWindowFlags.MenuBar or ImGuiWindowFlags.NoDocking

        val viewport = getMainViewport()
        val statusBarHeight = UiConstants.STATUS_BAR_HEIGHT

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

        menuBar.render(currentScene)// TODO: move

        end()
    }

    fun destroy() {
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()
        destroyContext()
    }
}
