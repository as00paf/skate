package com.pafoid.skate.app

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.EditorToast
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorPauseActions
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.GameSettingsBinding
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.EditorActionHandler
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.editor.ui.handlers.PlayModeHandler
import com.pafoid.skate.editor.systems.EditorResources
import com.pafoid.skate.editor.ui.handlers.VcsActionHandler
import com.pafoid.skate.editor.vcs.VcsRunner
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.engine.assets.Assets.Strings.GAME_STRINGS
import com.pafoid.skate.editor.assetimport.blender.BlenderSync
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.input.PointerSource

class Editor(
    private val engine: Engine,
    projectManager: ProjectManager,
) {
    val settingsManager =
        EditorSettingsManager(engine.serializer, engine.eventSystem, engine.logger, engine.stringManager)
    val clipboardService = ClipboardService(engine.serializer)
    val editorInputState = EditorInputState()
    /** Game Viewport focus/hover, reported by its window and read by the camera input (EDT-73). */
    val viewportFocus = com.pafoid.skate.editor.data.ViewportFocusReport()

    init {
        engine.uiAuthoring = true
        // EDT-57: Show tooltips and UI scale come from the editor settings, read live.
        com.pafoid.skate.editor.imgui.Tooltips.enabled = { settingsManager.editorSettings.showTooltips }
        com.pafoid.skate.editor.imgui.ImGuiStyleManager.uiScale = { settingsManager.editorSettings.fontSize }
        EditorPauseActions.install(engine.pauseActions, engine.eventSystem)
        // The game's language lives in the project's game settings, never in the editor's (GUI-10, EDT-39).
        GameSettingsBinding.install(engine.eventSystem, engine.gameLocale, engine.logger)
        // One pointer for picking, gizmos and runtime UI: ImGui's mouse is in the same desktop space as the
        // viewport image rect (also with multi-viewports); the GLFW cursor is relative to the main window.
        engine.inputProvider.mouseListener.pointerSource = PointerSource { out ->
            out[0] = imgui.ImGui.getMousePosX()
            out[1] = imgui.ImGui.getMousePosY()
        }
    }

    val undoRedoManager = UndoRedoManager(engine.eventSystem, engine.logger)

    /** Play/Pause/Stop/Reset (EDT-45); subscribed before any window so project close always stops Play first. */
    val playModeHandler = PlayModeHandler(engine, undoRedoManager).also { it.init() }

    val editorInputHandler =
        EditorInputHandler(
            clipboardService, undoRedoManager, editorInputState, engine, settingsManager, viewportFocus,
            textInputActive = { imgui.ImGui.getIO().wantTextInput },
        )
    val editorCamera = EditorCamera(engine, editorInputState) { settingsManager.editorSettings.camera }

    val editorActionHandler =
        EditorActionHandler(
            engine,
            undoRedoManager,
            projectManager,
            engine.stringManager,
            settingsManager,
            editorCamera
        )

    /** The project's local Git repository (EDT-60), shared by the branch button, the VCS dialogs and the Git log (EDT-79). */
    val vcsSession = VcsSession(
        projectRoot = { projectManager.currentProject?.getProjectFile()?.absoluteFile?.parentFile },
        runner = VcsRunner.Jobs(engine.jobSystem),
    )
    val vcsActionHandler = VcsActionHandler(
        engine.eventSystem, undoRedoManager, vcsSession,
        playing = { engine.runtimePlaying },
        unsavedScenes = { engine.sceneManager.openScenes.any { it.isDirty } },
        projectPath = { projectManager.currentProject?.projectPath },
        notify = { key, args -> EditorToast.show(engine.stringManager.getString(key, *args.toTypedArray())) },
    )

    val gizmoSystem = GizmoSystem(engine, settingsManager, undoRedoManager, editorCamera)

    /** Imports `.blend` files under the project's Assets and re-imports them when saved (AST-19). */
    val blenderSync = BlenderSync(engine, projectManager, { settingsManager.editorSettings })

    val imGuiLayer = ImGuiLayer(engine, this, projectManager)
    val editorEventHandler = EditorEventHandler(engine.eventSystem, imGuiLayer, undoRedoManager)

    fun init(window: Window) {
        // Menus, tooltips and the Key Bindings window show the live bindings (EDT-10).
        EditorShortcuts.mappings = { settingsManager.editorSettings.editorInputMappings }
        engine.cameraManager.camera = editorCamera.camera
        engine.cameraManager.transform = editorCamera.transform
        editorEventHandler.init()

        engine.jobSystem.runIO {
            settingsManager.loadSettings()
            // Wire the MSAA callback so the editor recreates the scene frame buffer when samples change.
            settingsManager.onMsaaChanged = { samples ->
                engine.renderer.resizeFrameBufferWithMsaa(engine.renderer.frameBuffer.width, engine.renderer.frameBuffer.height, samples)
            }
            engine.stringManager.loadStrings(EditorResources.path("strings")) // jar or src/main/resources (AST-26 NQ1)
            // Runtime UI in the viewport (HUD, showcase, pause menu) has its own strings and locale (GUI-10).
            engine.gameStrings.addStrings(GAME_STRINGS)
            engine.stringManager.setLocale(settingsManager.editorSettings.language)
        }

        imGuiLayer.init(window.windowController)
    }

    fun update(dt: Float) {
        editorInputHandler.updateAlways()
        // Play/Stop/Reset run here, before any window draws (EDT-45 review N5).
        playModeHandler.update()
        if (engine.sceneManager.currentScene?.isRunning == false) {
            editorInputHandler.update()
            editorCamera.update(dt)
            gizmoSystem.update(dt)
        }
        blenderSync.update()
        imGuiLayer.update(dt)
    }

    fun destroy() {
        playModeHandler.shutdown()
        // Keep the current scene's editor camera for the next session (EDT-58).
        editorActionHandler.sceneActionHandler.cameraMemory.flush()
        imGuiLayer.destroy()
    }
}