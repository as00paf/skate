package com.pafoid.skate.engine.imgui

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.editor.BoneTreeWindow
import com.pafoid.skate.engine.editor.EnvironmentWindow
import com.pafoid.skate.engine.editor.GameViewWindow
import com.pafoid.skate.engine.editor.PrefabsWindow
import com.pafoid.skate.engine.editor.PropertiesWindow
import com.pafoid.skate.engine.editor.SceneHierarchyWindow
import com.pafoid.skate.engine.editor.ThreadMonitorWindow
import com.pafoid.skate.engine.scenes.Scene
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
import imgui.internal.ImGui.dockBuilderRemoveNode
import imgui.internal.ImGui.dockBuilderSetNodeSize
import imgui.type.ImBoolean
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW
import sun.security.krb5.internal.KDCOptions.with
import java.io.File

private const val fontsFile = "assets/fonts/Font Awesome 7 Free-Solid-900.otf"
private const val imGuiFile = "imgui.ini"

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

        with(ImGui.getIO()) {
            iniFilename = imGuiFile
            backendPlatformName = "imgui_java_impl_glfw"
            addConfigFlags(ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.ViewportsEnable)
            loadFonts(fontsFile)
        }

        imGuiGlfw.init(glfwWindow, true)
        imGuiGl3.init(glslVersion)

        ImGuiStyleManager.setupStyle()
    }

    private fun setupLayout(dockspaceId: Int) {
        val iniFile = File("imgui.ini")
        if (iniFile.exists()) return

        dockBuilderRemoveNode(dockspaceId)
        dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.None)
        dockBuilderSetNodeSize(
            dockspaceId,
            ImGui.getMainViewport().sizeX,
            ImGui.getMainViewport().sizeY
        )

        val mainBodyId = ImInt(0)
        val leftId = imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.2f, null, mainBodyId)
        val rightId =
            imgui.internal.ImGui.dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Right, 0.25f, null, mainBodyId)
        val bottomId =
            imgui.internal.ImGui.dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Down, 0.25f, null, mainBodyId)

        imgui.internal.ImGui.dockBuilderDockWindow("Scene Hierarchy", leftId)
        imgui.internal.ImGui.dockBuilderDockWindow("Prefabs", leftId)
        imgui.internal.ImGui.dockBuilderDockWindow("Properties", rightId)
        imgui.internal.ImGui.dockBuilderDockWindow("Objects", bottomId)
        imgui.internal.ImGui.dockBuilderDockWindow("Game Viewport", mainBodyId.get())

        imgui.internal.ImGui.dockBuilderFinish(dockspaceId)
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
                    Window.Companion.setVSync(settings.vsync)
                    SettingsManager.save()
                }

                val fullscreen = ImBoolean(settings.fullscreen)
                if (ImGui.checkbox("Fullscreen", fullscreen)) {
                    settings.fullscreen = fullscreen.get()
                    Window.Companion.setFullscreen(settings.fullscreen)
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