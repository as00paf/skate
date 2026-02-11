package com.pafoid.skate.engine.imgui

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.editor.*
import com.pafoid.skate.engine.editor.assetBrowser.AssetBrowser
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.ClipboardService
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.utils.Icons
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.StringManager
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.ImVec2
import imgui.flag.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.internal.ImGui.*
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
    private val renderer: Renderer
): KoinComponent {

    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()
    private val glslVersion = "#version 330"
    private var glfwWindow: Long = 0

    val propertiesWindow = PropertiesWindow()
    val boneTreeWindow = BoneTreeWindow()
    val gameViewWindow = GameViewWindow()
    val assetBrowser = AssetBrowser()
    val consoleWindow = ConsoleWindow()
    private val physicsTunerWindow = PhysicsTunerWindow()
    private val environmentWindow = EnvironmentWindow()
    private val profilerWindow = ProfilerWindow()
    private val hierarchyWindow = SceneHierarchyWindow()

    // Window Visibility Flags
    private val showHierarchy = ImBoolean(true)
    private val showProperties = ImBoolean(true)
    private val showBoneTree = ImBoolean(true)
    private val showGameView = ImBoolean(true)
    private val showAssetBrowser = ImBoolean(true)
    private val showEnvironment = ImBoolean(true)
    private val showProfiler = ImBoolean(true)
    private val showConsole = ImBoolean(true)
    private val showPhysicsTuner = ImBoolean(true)
    private var showKeyBindings = false
    private var keyBindingAction: String? = null
    private var isViewportMaximized = false

    private lateinit var setFullscreen: (Boolean) -> Unit
    private lateinit var setVSync: (Boolean) -> Unit

    fun init(glfwWindow: Long, fullScreenCallback:(Boolean)->Unit, vSyncCallback:(Boolean)->Unit) {
        this.glfwWindow = glfwWindow
        this.setFullscreen = fullScreenCallback
        this.setVSync = vSyncCallback

        ImGui.createContext()

        with(ImGui.getIO()) {
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
            ImGui.getMainViewport().sizeX,
            ImGui.getMainViewport().sizeY
        )

        val mainBodyId = ImInt(0)
        val leftId = dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.2f, null, mainBodyId)
        val rightId =
            dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Right, 0.25f, null, mainBodyId)
        val bottomId =
            dockBuilderSplitNode(mainBodyId.get(), ImGuiDir.Down, 0.25f, null, mainBodyId)

        dockBuilderDockWindow(stringManager.getString("window.hierarchy"), leftId)
        dockBuilderDockWindow(stringManager.getString("window.asset_browser"), leftId)
        dockBuilderDockWindow(stringManager.getString("window.properties"), leftId)
        dockBuilderDockWindow(stringManager.getString("window.bonetree"), leftId)

        dockBuilderDockWindow(stringManager.getString("window.game_viewport"), mainBodyId.get())

        dockBuilderDockWindow(stringManager.getString("window.console"), bottomId)
        dockBuilderDockWindow(stringManager.getString("window.profiler"), bottomId)
        dockBuilderDockWindow(stringManager.getString("window.environment"), bottomId)
        dockBuilderDockWindow(stringManager.getString("window.physics_tuner"), bottomId)

        dockBuilderFinish(dockspaceId)
    }

    fun update(dt: Float, currentScene: Scene) {
        if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_F12)) {
            isViewportMaximized = !isViewportMaximized
        }
        
        startFrame()

        if (isViewportMaximized) {
            ImGui.setNextWindowPos(ImGui.getMainViewport().workPosX, ImGui.getMainViewport().workPosY)
            ImGui.setNextWindowSize(ImGui.getMainViewport().workSizeX, ImGui.getMainViewport().workSizeY)
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            ImGui.begin(stringManager.getString("window.game_viewport") + " Maximized", ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoDecoration)
            
            val windowSize = ImVec2()
            ImGui.getContentRegionAvail(windowSize)
            
            val texId = renderer.frameBuffer.getTextureId()
            ImGui.image(texId.toLong(), windowSize.x, windowSize.y, 0f, 1f, 1f, 0f)
            
            ImGui.end()
            ImGui.popStyleVar()
        } else {
            setupDockSpace(currentScene)
            currentScene.imgui()
            
            if (showHierarchy.get()) hierarchyWindow.imgui(currentScene)
            if (showProperties.get()) propertiesWindow.imgui()
            if (showBoneTree.get()) boneTreeWindow.imgui()
            if (showGameView.get()) gameViewWindow.imgui()
            if (showAssetBrowser.get()) assetBrowser.imgui()
            if (showEnvironment.get()) environmentWindow.imgui(currentScene)
            if (showProfiler.get()) profilerWindow.imgui()
            if (showConsole.get()) consoleWindow.imgui(showConsole)
            if (showPhysicsTuner.get()) physicsTunerWindow.imgui(currentScene)
            if (showKeyBindings) renderKeyBindingsWindow()
        }

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
        ImGui.begin(stringManager.getString("lbl.editor_title"), ImBoolean(true), windowFlags)
        ImGui.popStyleVar(2)

        ImGui.dockSpace(ImGui.getID("DockSpace"))
        setupLayout(ImGui.getID("DockSpace"))

        if (ImGui.beginMenuBar()) {
            if (ImGui.beginMenu(stringManager.getString("menu.file"))) {
                if (ImGui.menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save")}", "Ctrl+S")) {
                    currentScene.save()
                }
                if (ImGui.menuItem("${Icons.SAVE} ${stringManager.getString("menu.file.save_as")}")) {
                    currentScene.saveAs()
                }
                if (ImGui.menuItem("${Icons.FOLDER_OPEN} ${stringManager.getString("menu.file.open")}", "Ctrl+O")) {
                    currentScene.open()
                }
                ImGui.separator()
                if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("menu.file.quit")}")) {
                    GLFW.glfwSetWindowShouldClose(glfwWindow, true)
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu(stringManager.getString("menu.edit"))) {
                if (ImGui.menuItem("${Icons.UNDO} ${stringManager.getString("menu.edit.undo")}", "Ctrl+Z")) {
                    sceneManager.undo()
                }
                if (ImGui.menuItem("${Icons.REDO} ${stringManager.getString("menu.edit.redo")}", "Ctrl+Y")) {
                    sceneManager.redo()
                }
                ImGui.separator()
                if (ImGui.menuItem("${Icons.CUT} ${stringManager.getString("menu.edit.cut")}", "Ctrl+X")) {
                    val selected = sceneManager.currentScene?.getSelectedGameObject()
                    if (selected != null) {
                        clipboardService.copy(selected)
                        sceneManager.deleteGameObject(selected)
                    }
                }
                if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("menu.edit.copy")}", "Ctrl+C")) {
                    sceneManager.currentScene?.getSelectedGameObject()?.let {
                        clipboardService.copy(it)
                    }
                }
                if (ImGui.menuItem("${Icons.PASTE} ${stringManager.getString("menu.edit.paste")}", "Ctrl+V")) {
                    val cloned = clipboardService.paste()
                    if (cloned != null) {
                        cloned.getComponent<Transform>()?.translation?.set(0f, 0f, 0f)
                        cloned.parent = null
                        sceneManager.addGameObject(cloned)
                    }
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu(stringManager.getString("menu.settings"))) {
                val settings = settingsManager.settings

                val vsync = ImBoolean(settings.vsync)
                if (ImGui.checkbox(stringManager.getString("menu.settings.vsync"), vsync)) {
                    settings.vsync = vsync.get()
                    setVSync(settings.vsync)
                    settingsManager.save()
                }

                val fullscreen = ImBoolean(settings.fullscreen)
                if (ImGui.checkbox(stringManager.getString("menu.settings.fullscreen"), fullscreen)) {
                    settings.fullscreen = fullscreen.get()
                    setFullscreen(settings.fullscreen)
                    settingsManager.save()
                }

                ImGui.separator()
                val overlaySize = floatArrayOf(settings.gamepadOverlaySize)
                if (ImGui.sliderFloat(stringManager.getString("menu.settings.gamepad_overlay_size"), overlaySize, 0.05f, 0.5f)) {
                    settings.gamepadOverlaySize = overlaySize[0]
                    settingsManager.save()
                }

                val showOverlay = ImBoolean(settings.showGamepadOverlay)
                if (ImGui.checkbox(stringManager.getString("menu.settings.show_gamepad_overlay"), showOverlay)) {
                    settings.showGamepadOverlay = showOverlay.get()
                    settingsManager.save()
                }

                ImGui.separator()
                val unitSystems = UnitSystem.entries.toTypedArray()
                val currentUnitIdx = ImInt(settings.unitSystem.ordinal)
                if (ImGui.combo(stringManager.getString("menu.settings.unit_system"), currentUnitIdx, unitSystems.map { it.name }.toTypedArray())) {
                    settings.unitSystem = unitSystems[currentUnitIdx.get()]
                    settingsManager.save()
                }

                ImGui.separator()
                val languages = arrayOf("en", "fr") // Add more languages here
                val currentLangIdx = ImInt(languages.indexOf(settings.language))
                if (ImGui.combo(stringManager.getString("menu.settings.language"), currentLangIdx, languages, languages.size)) {
                    val newLang = languages[currentLangIdx.get()]
                    settings.language = newLang
                    settingsManager.setLocale(newLang) // This will also reload StringManager
                    settingsManager.save()
                }

                ImGui.separator()
                if (ImGui.menuItem(stringManager.getString("menu.settings.keybindings"))) {
                    showKeyBindings = true
                }

                ImGui.endMenu()
            }
            if (ImGui.beginMenu(stringManager.getString("menu.view"))) {
                if (ImGui.beginMenu(stringManager.getString("menu.view.windows"))) {
                    ImGui.checkbox(stringManager.getString("window.hierarchy"), showHierarchy)
                    ImGui.checkbox(stringManager.getString("window.properties"), showProperties)
                    ImGui.checkbox(stringManager.getString("window.bonetree"), showBoneTree)
                    ImGui.checkbox(stringManager.getString("window.game_viewport"), showGameView)
                    ImGui.checkbox(stringManager.getString("window.asset_browser"), showAssetBrowser)
                    ImGui.checkbox(stringManager.getString("window.environment"), showEnvironment)
                    ImGui.checkbox(stringManager.getString("window.profiler"), showProfiler)
                    ImGui.checkbox(stringManager.getString("window.console"), showConsole)
                    ImGui.checkbox(stringManager.getString("window.physics_tuner"), showPhysicsTuner)
                    ImGui.endMenu()
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

    private fun renderKeyBindingsWindow() {
        if (ImGui.begin(stringManager.getString("window.keybindings"), ImGuiWindowFlags.AlwaysAutoResize)) {
            val settings = settingsManager.settings.keyBindings
            
            // Helper to draw a row
            fun drawBindRow(label: String, currentKey: Int, bindAction: String) {
                ImGui.text(label)
                ImGui.sameLine(200f)
                
                val keyName = getKeyName(currentKey)
                val btnText = if (keyBindingAction == bindAction) stringManager.getString("lbl.keybindings.press_key") else keyName
                
                if (ImGui.button("$btnText##$bindAction", 120f, 0f)) {
                    keyBindingAction = bindAction
                }
            }

            drawBindRow(stringManager.getString("lbl.keybindings.translate"), settings.gizmoTranslate, "gizmoTranslate")
            drawBindRow(stringManager.getString("lbl.keybindings.rotate"), settings.gizmoRotate, "gizmoRotate")
            drawBindRow(stringManager.getString("lbl.keybindings.scale"), settings.gizmoScale, "gizmoScale")
            drawBindRow(stringManager.getString("lbl.keybindings.select"), settings.gizmoSelect, "gizmoSelect")
            drawBindRow(stringManager.getString("lbl.keybindings.measure"), settings.gizmoMeasure, "gizmoMeasure")
            drawBindRow(stringManager.getString("lbl.keybindings.deselect"), settings.deselect, "deselect")

            ImGui.separator()
            if (ImGui.button(stringManager.getString("btn.close"))) {
                showKeyBindings = false
                keyBindingAction = null
                settingsManager.save()
            }
            
            // Handle Binding
            if (keyBindingAction != null) {
                // Check for key press
                for (i in 0..348) { // GLFW_KEY_LAST is 348
                    if (ImGui.isKeyPressed(i)) {
                        // Assign key
                        when (keyBindingAction) {
                            "gizmoTranslate" -> settings.gizmoTranslate = i
                            "gizmoRotate" -> settings.gizmoRotate = i
                            "gizmoScale" -> settings.gizmoScale = i
                            "gizmoSelect" -> settings.gizmoSelect = i
                            "gizmoMeasure" -> settings.gizmoMeasure = i
                            "deselect" -> settings.deselect = i
                        }
                        keyBindingAction = null
                        settingsManager.save()
                        break
                    }
                }
            }
        }
        ImGui.end()
    }

    private fun getKeyName(key: Int): String {
        return when (key) {
            GLFW.GLFW_KEY_ESCAPE -> "Esc"
            GLFW.GLFW_KEY_ENTER -> "Enter"
            GLFW.GLFW_KEY_TAB -> "Tab"
            GLFW.GLFW_KEY_BACKSPACE -> "Backspace"
            GLFW.GLFW_KEY_INSERT -> "Insert"
            GLFW.GLFW_KEY_DELETE -> "Delete"
            GLFW.GLFW_KEY_RIGHT -> "Right"
            GLFW.GLFW_KEY_LEFT -> "Left"
            GLFW.GLFW_KEY_DOWN -> "Down"
            GLFW.GLFW_KEY_UP -> "Up"
            GLFW.GLFW_KEY_PAGE_UP -> "PgUp"
            GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn"
            GLFW.GLFW_KEY_HOME -> "Home"
            GLFW.GLFW_KEY_END -> "End"
            else -> {
                val name = GLFW.glfwGetKeyName(key, 0)
                name?.uppercase() ?: "Key $key"
            }
        }
    }
}