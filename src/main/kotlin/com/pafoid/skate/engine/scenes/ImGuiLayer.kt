package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.editor.BoneTreeWindow
import com.pafoid.skate.engine.editor.EnvironmentWindow
import com.pafoid.skate.engine.editor.GameViewWindow
import com.pafoid.skate.engine.editor.PrefabsWindow
import com.pafoid.skate.engine.editor.PropertiesWindow
import com.pafoid.skate.engine.editor.SceneHierarchyWindow
import com.pafoid.skate.engine.editor.ThreadMonitorWindow
import com.pafoid.skate.engine.utils.Icons
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImFontConfig
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
import imgui.internal.ImGui.dockBuilderAddNode
import imgui.internal.ImGui.dockBuilderDockWindow
import imgui.internal.ImGui.dockBuilderFinish
import imgui.internal.ImGui.dockBuilderRemoveNode
import imgui.internal.ImGui.dockBuilderSetNodeSize
import imgui.internal.ImGui.dockBuilderSplitNode
import imgui.type.ImBoolean
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW
import java.io.File

class ImGuiLayer {

    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"
    private var glfwWindow: Long = 0

    private var firstFrame = true

    val propertiesWindow = PropertiesWindow()
    val boneTreeWindow = BoneTreeWindow()
    val gameViewWindow = GameViewWindow()
    val prefabsWindow = PrefabsWindow()
    private val environmentWindow = EnvironmentWindow()
    private val threadMonitorWindow = ThreadMonitorWindow()
    private val hierarchyWindow = SceneHierarchyWindow(propertiesWindow, boneTreeWindow)

    fun init(glfwWindow: Long) {
        this.glfwWindow = glfwWindow
        ImGui.createContext()

        val io = ImGui.getIO()
        io.iniFilename = "imgui.ini"
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.ViewportsEnable)
        io.backendPlatformName = "imgui_java_impl_glfw"

        // Load Fonts
        val fontAtlas = io.fonts
        val fontConfig = ImFontConfig()

        // Default Font
        fontAtlas.addFontDefault()

        // Merge FontAwesome
        fontConfig.mergeMode = true
        fontConfig.pixelSnapH = true
        fontConfig.glyphMinAdvanceX = 14f // Use size of font

        val iconRanges = shortArrayOf(0xe000.toShort(), 0xf8ff.toShort(), 0)
        fontAtlas.addFontFromFileTTF("assets/fonts/Font Awesome 7 Free-Solid-900.otf", 14f, fontConfig, iconRanges)

        fontAtlas.build()
        fontConfig.destroy()

        imGuiGlfw.init(glfwWindow, true) // TRUE means install callbacks
        setupStyle()
        imGuiGl3.init(glslVersion)
    }

    private fun setupStyle() {
        val style = ImGui.getStyle()

        // Pro Dark Theme (Slate / Charcoal)
        style.windowRounding = 4f
        style.childRounding = 4f
        style.frameRounding = 4f
        style.grabRounding = 4f
        style.popupRounding = 4f
        style.scrollbarRounding = 4f

        style.setColor(ImGuiCol.Text, 0.90f, 0.90f, 0.90f, 1.00f)
        style.setColor(ImGuiCol.TextDisabled, 0.60f, 0.60f, 0.60f, 1.00f)
        style.setColor(ImGuiCol.WindowBg, 0.13f, 0.14f, 0.17f, 1.00f)
        style.setColor(ImGuiCol.ChildBg, 0.13f, 0.14f, 0.17f, 1.00f)
        style.setColor(ImGuiCol.PopupBg, 0.13f, 0.14f, 0.17f, 1.00f)
        style.setColor(ImGuiCol.Border, 0.26f, 0.26f, 0.26f, 1.00f)
        style.setColor(ImGuiCol.BorderShadow, 0.00f, 0.00f, 0.00f, 0.00f)
        style.setColor(ImGuiCol.FrameBg, 0.21f, 0.22f, 0.26f, 1.00f)
        style.setColor(ImGuiCol.FrameBgHovered, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.FrameBgActive, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.TitleBg, 0.13f, 0.14f, 0.17f, 1.00f)
        style.setColor(ImGuiCol.TitleBgActive, 0.13f, 0.14f, 0.17f, 1.00f)
        style.setColor(ImGuiCol.TitleBgCollapsed, 0.13f, 0.14f, 0.17f, 1.00f)
        style.setColor(ImGuiCol.MenuBarBg, 0.13f, 0.14f, 0.17f, 1.00f)
        style.setColor(ImGuiCol.ScrollbarBg, 0.13f, 0.14f, 0.17f, 1.00f)
        style.setColor(ImGuiCol.ScrollbarGrab, 0.31f, 0.31f, 0.31f, 1.00f)
        style.setColor(ImGuiCol.ScrollbarGrabHovered, 0.41f, 0.41f, 0.41f, 1.00f)
        style.setColor(ImGuiCol.ScrollbarGrabActive, 0.51f, 0.51f, 0.51f, 1.00f)
        style.setColor(ImGuiCol.CheckMark, 0.80f, 0.80f, 0.80f, 1.00f)
        style.setColor(ImGuiCol.SliderGrab, 0.39f, 0.39f, 0.39f, 1.00f)
        style.setColor(ImGuiCol.SliderGrabActive, 0.51f, 0.51f, 0.51f, 1.00f)
        style.setColor(ImGuiCol.Button, 0.21f, 0.22f, 0.26f, 1.00f)
        style.setColor(ImGuiCol.ButtonHovered, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.ButtonActive, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.Header, 0.21f, 0.22f, 0.26f, 1.00f)
        style.setColor(ImGuiCol.HeaderHovered, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.HeaderActive, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.Separator, 0.21f, 0.22f, 0.26f, 1.00f)
        style.setColor(ImGuiCol.SeparatorHovered, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.SeparatorActive, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.ResizeGrip, 0.21f, 0.22f, 0.26f, 1.00f)
        style.setColor(ImGuiCol.ResizeGripHovered, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.ResizeGripActive, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.Tab, 0.21f, 0.22f, 0.26f, 1.00f)
        style.setColor(ImGuiCol.TabHovered, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.TabActive, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.TabUnfocused, 0.21f, 0.22f, 0.26f, 1.00f)
        style.setColor(ImGuiCol.TabUnfocusedActive, 0.30f, 0.31f, 0.36f, 1.00f)
        style.setColor(ImGuiCol.PlotLines, 0.61f, 0.61f, 0.61f, 1.00f)
        style.setColor(ImGuiCol.PlotLinesHovered, 1.00f, 0.43f, 0.35f, 1.00f)
        style.setColor(ImGuiCol.PlotHistogram, 0.90f, 0.70f, 0.00f, 1.00f)
        style.setColor(ImGuiCol.PlotHistogramHovered, 1.00f, 0.60f, 0.00f, 1.00f)
        style.setColor(ImGuiCol.TextSelectedBg, 0.26f, 0.59f, 0.98f, 0.35f)
        style.setColor(ImGuiCol.DragDropTarget, 1.00f, 1.00f, 0.00f, 0.90f)
        style.setColor(ImGuiCol.NavHighlight, 0.26f, 0.59f, 0.98f, 1.00f)
        style.setColor(ImGuiCol.NavWindowingHighlight, 1.00f, 1.00f, 1.00f, 0.70f)
        style.setColor(ImGuiCol.NavWindowingDimBg, 0.80f, 0.80f, 0.80f, 0.20f)
        style.setColor(ImGuiCol.ModalWindowDimBg, 0.80f, 0.80f, 0.80f, 0.35f)
    }

    private fun setupLayout(dockspaceId: Int) {
        if (!firstFrame) return
        firstFrame = false

        val iniFile = File("imgui.ini")
        if (iniFile.exists()) return

        dockBuilderRemoveNode(dockspaceId)
        dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.None)
        dockBuilderSetNodeSize(dockspaceId, ImGui.getMainViewport().sizeX, ImGui.getMainViewport().sizeY)

        val mainBodyId = ImInt(0)
        val leftId = dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.2f, null, mainBodyId)
        val rightId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Right, 0.25f, null, mainBodyId)
        val bottomId = dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Down, 0.25f, null, mainBodyId)

        dockBuilderDockWindow("Scene Hierarchy", leftId)
        dockBuilderDockWindow("Prefabs", leftId)
        dockBuilderDockWindow("Properties", rightId)
        dockBuilderDockWindow("Objects", bottomId)
        dockBuilderDockWindow("Game Viewport", mainBodyId.get())

        dockBuilderFinish(dockspaceId)
    }

    fun update(dt: Float, currentScene: Scene) {
        startFrame()

        setupDockSpace(currentScene)
        currentScene.imgui()
        hierarchyWindow.imgui(currentScene)
        propertiesWindow.imgui()
        boneTreeWindow.imgui()
        gameViewWindow.imgui()
        prefabsWindow.imgui()
        environmentWindow.imgui(currentScene)
        threadMonitorWindow.imgui()

        endFrame()
    }

    fun startFrame() {
        imGuiGlfw.newFrame()
        imGuiGl3.newFrame()
        ImGui.newFrame()
    }

    fun endFrame() {
        ImGui.render()
        imGuiGl3.renderDrawData(ImGui.getDrawData())

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            val backupWindowPtr = GLFW.glfwGetCurrentContext()
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()
            GLFW.glfwMakeContextCurrent(backupWindowPtr)
        }
    }

    private fun setupDockSpace(currentScene: Scene) {
        var windowFlags = ImGuiWindowFlags.MenuBar or ImGuiWindowFlags.NoDocking

        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(viewport.workPosX, viewport.workPosY)
        ImGui.setNextWindowSize(viewport.workSizeX, viewport.workSizeY)
        ImGui.setNextWindowViewport(viewport.id)
        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.Always)

        windowFlags = windowFlags or (ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus)

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f)
        ImGui.begin("DockSpace Demo", ImBoolean(true), windowFlags)
        ImGui.popStyleVar(2)

        ImGui.dockSpace(ImGui.getID("DockSpace"))
        setupLayout(ImGui.getID("DockSpace"))

        if (ImGui.beginMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("${Icons.SAVE} Save Level", "Ctrl+S")) {
                    currentScene.save()
                }
                if (ImGui.menuItem("${Icons.SAVE} Save As...")) {
                    currentScene.saveAs()
                }
                if (ImGui.menuItem("${Icons.FOLDER_OPEN} Open Level", "Ctrl+O")) {
                    currentScene.open()
                }
                ImGui.separator()
                if (ImGui.menuItem("${Icons.TRASH} Quit")) {
                    GLFW.glfwSetWindowShouldClose(glfwWindow, true)
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("Settings")) {
                val settings = SettingsManager.settings

                val vsync = ImBoolean(settings.vsync)
                if (ImGui.checkbox("V-Sync", vsync)) {
                    settings.vsync = vsync.get()
                    Window.setVSync(settings.vsync)
                    SettingsManager.save()
                }

                val fullscreen = ImBoolean(settings.fullscreen)
                if (ImGui.checkbox("Fullscreen", fullscreen)) {
                    settings.fullscreen = fullscreen.get()
                    Window.setFullscreen(settings.fullscreen)
                    SettingsManager.save()
                }

                ImGui.separator()
                val overlaySize = floatArrayOf(settings.gamepadOverlaySize)
                if (ImGui.sliderFloat("Gamepad Overlay Size", overlaySize, 0.05f, 0.5f)) {
                    settings.gamepadOverlaySize = overlaySize[0]
                    SettingsManager.save()
                }

                val showOverlay = ImBoolean(settings.showGamepadOverlay)
                if (ImGui.checkbox("Show Gamepad Overlay", showOverlay)) {
                    settings.showGamepadOverlay = showOverlay.get()
                    SettingsManager.save()
                }

                ImGui.separator()
                val unitSystems = UnitSystem.entries.toTypedArray()
                val currentUnitIdx = ImInt(settings.unitSystem.ordinal)
                if (ImGui.combo("Unit System", currentUnitIdx, unitSystems.map { it.name }.toTypedArray())) {
                    settings.unitSystem = unitSystems[currentUnitIdx.get()]
                    SettingsManager.save()
                }

                ImGui.endMenu()
            }
            if (ImGui.beginMenu("Create")) {
                if (ImGui.menuItem("Cube")) {
                    // Spawning a cube logic
                }
                if (ImGui.menuItem("Sprite")) {
                    // Spawning a sprite logic
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("View")) {
                ImGui.text("FPS: ${(1.0f / ImGui.getIO().deltaTime).toInt()}")
                ImGui.separator()
                val debugEnabled = ImBoolean(currentScene.physics3d.debugEnabled)
                if (ImGui.checkbox("Physics Debug", debugEnabled)) {
                    currentScene.physics3d.debugEnabled = debugEnabled.get()
                }
                ImGui.endMenu()
            }
            ImGui.endMenuBar()
        }

        ImGui.end()
    }

    fun destroy() {
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()
        ImGui.destroyContext()
    }
}