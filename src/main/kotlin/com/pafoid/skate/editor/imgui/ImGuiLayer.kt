package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.systems.EditorResources
import com.pafoid.skate.editor.systems.EditorPaths
import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.systems.CloseTarget
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.data.UiConstants
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import java.io.File
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.WindowController
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

class ImGuiLayer(
    private val engine: Engine,
    editor: Editor,
    projectManager: ProjectManager,
) {
    private val stringManager = engine.stringManager
    private val settingsManager = editor.settingsManager
    private val closeGuard = editor.closeGuard

    /** The editor-wide unsaved-assets prompt (EDT-82), drawn after the windows. */
    private val unsavedAssetsModal = UnsavedAssetsModal(engine.stringManager, closeGuard)
    private val logger = engine.logger
    private val eventSystem = engine.eventSystem
    private val renderer = engine.renderer
    private val inputProvider = engine.inputProvider

    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"

    private val tempVec2 = ImVec2()

    var isViewportMaximized = false

    private var layoutInitialized = false
    private var forceLayoutRebuild = false

    private val layoutStore = EditorLayoutStore(
        EditorPaths.file(EditorPaths.IMGUI_INI), EditorPaths.file(EditorPaths.OPEN_WINDOWS_FILE),
    )

    private val windowRegistry = WindowRegistry(engine, editor, projectManager)
    private val windowManager = WindowManager(stringManager, windowRegistry, engine.eventSystem)

    // EDT-88: maximize; its layout changes run at safe points of the frame.
    private val maximize = MaximizeDriver(
        windowOf = { windowRegistry.getWindow(it) },
        titleOf = { window, id -> window.title(stringManager, id = id) },
        saveLayout = { saveLayout() },
        setAutosave = { on -> getIO().iniFilename = if (on) layoutStore.iniFile.path else null },
    )

    fun init(windowController: WindowController) {
        createContext()
        // Only the editor has an ImGui context; engine input asks through these hooks (GUI-14).
        ImGuiCaptureHooks.install(inputProvider, { getIO().wantCaptureKeyboard }, { getIO().wantCaptureMouse })

        with(getIO()) {
            iniFilename = layoutStore.iniFile.path
            backendPlatformName = "imgui_java_impl_glfw"
            addConfigFlags(ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.ViewportsEnable)
            val fontsByteArray =
                this.javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)?.readAllBytes()
            fontsByteArray?.let { loadFonts(it) }
        }

        imGuiGlfw.init(windowController.glfwWindow, true)
        imGuiGl3.init(glslVersion)

        loadThemes()
        MImGui.strings = stringManager

        layoutStore.removeDockIds(windowRegistry.windows.filterIsInstance<EditorDialog>().map { it.name })
        windowManager.init()
        eventSystem.subscribe<WindowAction.ResetLayout> { resetLayout() }
        MaximizeSession.model = maximize.model
        MaximizeSession.defaultMode = { settingsManager.editorSettings.maximizeMode }
        eventSystem.subscribe<WindowAction.ToggleMaximizeFocused> { toggleMaximizeFocused() }
        eventSystem.subscribe<WindowAction.RestoreMaximized> { MaximizeSession.requestRestore() }
        eventSystem.subscribe<WindowAction.Show> { event ->
            if (windowRegistry.getWindow(event.name)?.isDialog == false) maximize.model.onWindowShown(event.name)
        }
        windowController.onToggleMaximize = { maximized -> windowRegistry.menuBar.setMaximized(maximized) }
        // EDT-82: File > Quit, the title bar's close button and the OS close (Alt+F4) ask about unsaved assets first.
        eventSystem.subscribe<EditorEvent.Exit> { closeGuard.request(CloseTarget.EDITOR) { windowController.close() } }
        GLFW.glfwSetWindowCloseCallback(windowController.glfwWindow) { handle ->
            if (closeGuard.unsaved().isNotEmpty()) {
                GLFW.glfwSetWindowShouldClose(handle, false)
                closeGuard.request(CloseTarget.EDITOR) { windowController.close() }
            }
        }
        eventSystem.subscribe<EditorEvent.Minimize> { windowController.minimize() }
        eventSystem.subscribe<EditorEvent.ToggleMaximize> { windowController.toggleMaximize() }
    }

    /** Reopens the windows that were open when the layout was last saved. Returns false if none were saved. */
    fun restoreWindowState(): Boolean {
        val open = layoutStore.loadOpenWindows() ?: return false
        windowRegistry.windows.forEach { it.isOpen.set(it !is EditorDialog && it.name in open) }
        return true
    }

    /** Writes the layout on screen, or [ini] (EDT-88: the layout from before maximizing), and the open windows. */
    private fun saveLayout(ini: String? = null) {
        if (ini == null) ImGui.saveIniSettingsToDisk(layoutStore.iniFile.path) else layoutStore.iniFile.writeText(ini)
        layoutStore.saveOpenWindows(windowRegistry.dockableWindows.filter { it.isOpen.get() }.map { it.name })
    }

    private fun resetLayout() {
        // EDT-88 §8: the maximize state is dropped, not restored: the layout is replaced anyway.
        maximize.onResetLayout()
        layoutStore.clear()
        windowRegistry.showDefaultWindows()
        forceLayoutRebuild = true
        layoutInitialized = false
    }

    private fun setupLayout(dockspaceId: Int) {
        if (layoutInitialized) return
        layoutInitialized = true

        if (!forceLayoutRebuild && layoutStore.hasSavedDockLayout()) return
        forceLayoutRebuild = false

        dockBuilderRemoveNode(dockspaceId)
        dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.PassthruCentralNode)
        dockBuilderSetNodeSize(dockspaceId, getMainViewport().sizeX, getMainViewport().sizeY)

        val mainBodyId = ImInt(0)
        val leftId = dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, DefaultDockLayout.LEFT_RATIO, null, mainBodyId)
        val rightId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Right, DefaultDockLayout.RIGHT_RATIO, null, mainBodyId)
        val bottomId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Down, DefaultDockLayout.BOTTOM_RATIO, null, mainBodyId)

        // EDT-88: the viewport's node shows a tab bar only when another window is docked over it.
        DockLayoutFlags.applyCentral(ImGui.dockBuilderGetNode(mainBodyId.get()))

        windowManager.dockWindows(mainBodyId, leftId, rightId, bottomId)

        dockBuilderFinish(dockspaceId)
    }

    fun update(dt: Float) {
        maximize.beforeFrame()

        startFrame()

        if (isViewportMaximized) {
            setNextWindowPos(getMainViewport().workPosX, getMainViewport().workPosY, ImGuiCond.Always)
            setNextWindowSize(getMainViewport().workSizeX, getMainViewport().workSizeY, ImGuiCond.Always)
            begin(
                ImGuiIds.stable(
                    stringManager.getString("window.game_viewport.maximized", stringManager.getString("window.game_viewport")),
                    "window.game_viewport.maximized",
                ),
                ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoDecoration
            )

            getContentRegionAvail(tempVec2)

            val texId = renderer.frameBuffer.getTextureId()
            image(texId.toLong(), tempVec2.x, tempVec2.y, 0f, 1f, 1f, 0f)

            end()
        } else {
            setupDockSpace()
        }
        // EDT-82: at the frame's root, so the unsaved-assets prompt also shows over F12 viewport full screen.
        unsavedAssetsModal.render()

        endFrame()
    }

    /** EDT-07: reads the theme files and shows the saved theme. */
    private fun loadThemes() {
        ImGuiStyleManager.library = ThemeLibrary.load(File(EditorResources.path("themes")))
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.library.errors.forEach { logger.logEditor("Theme skipped: $it", LoggerService.LogLevel.WARN) }
        ImGuiStyleManager.sync(settingsManager.editorSettings.theme)
    }

    fun startFrame() {
        // A theme switch (settings window or saved setting) applies between frames.
        ImGuiStyleManager.sync(settingsManager.editorSettings.theme)
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

    private fun setupDockSpace() {
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

        // Stable ID: the dock space below is keyed on this window, so a translated title would drop the whole layout.
        begin(stringManager.label("lbl.editor_title"), ImBoolean(true), windowFlags)

        popStyleVar(4)

        // EDT-88 §3: the full-editor host covers the dock space's area, between the menu bar and the status bar.
        MaximizeSession.hostArea = MaximizeSession.Area(
            ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY(),
            ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(),
        )
        val dockspaceId = getID("DockSpace")
        setupLayout(dockspaceId)
        // While a window fills the editor the dock nodes stay alive but are not drawn, and their windows are skipped.
        dockSpace(dockspaceId, 0f, 0f, maximize.dockSpaceFlags())
        maximize.afterDockSpace(ImGui.dockBuilderGetCentralNode(dockspaceId))

        windowManager.update()

        maximize.afterWindows(engine.runtimePlaying)

        end()
    }

    /** EDT-88: the maximize shortcut acts on the last focused tool window; it turns F12 full screen off first. */
    private fun toggleMaximizeFocused() {
        val target = maximize.model.window ?: MaximizeSession.focused ?: return
        if (isViewportMaximized) eventSystem.publish(ViewportAction.ToggleFullScreen)
        MaximizeSession.request(target)
    }

    fun destroy() {
        // EDT-88: save the layout from before maximizing, never the temporary one.
        val held = maximize.onExit()
        if (held == null) saveLayout() else saveLayout(held)
        MaximizeSession.reset()
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()
        // Input read after this point must not touch the destroyed context (GUI-16).
        ImGuiCaptureHooks.reset(inputProvider)
        destroyContext()
    }
}
