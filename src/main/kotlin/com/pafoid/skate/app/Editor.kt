package com.pafoid.skate.app

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.EditorActionHandler
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.engine.assets.Assets.Strings.EDITOR_STRINGS_EN
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window

class Editor(
    private val engine: Engine,
    projectManager: ProjectManager,
) {
    val settingsManager =
        EditorSettingsManager(engine.serializer, engine.eventSystem, engine.logger, engine.stringManager)
    val clipboardService = ClipboardService(engine.serializer)
    val editorInputState = EditorInputState()

    val undoRedoManager = UndoRedoManager(engine.eventSystem, engine.logger)

    val editorInputHandler =
        EditorInputHandler(clipboardService, undoRedoManager, editorInputState, engine, settingsManager)
    val editorCamera = EditorCamera(editorInputState)

    val editorActionHandler =
        EditorActionHandler(
            engine,
            undoRedoManager,
            projectManager,
            engine.stringManager,
            settingsManager,
            editorCamera
        )

    val gizmoSystem = GizmoSystem(engine, settingsManager, undoRedoManager, editorCamera)

    val imGuiLayer = ImGuiLayer(engine, this, projectManager)
    val editorEventHandler = EditorEventHandler(engine.eventSystem, imGuiLayer, undoRedoManager)

    fun init(window: Window) {
        engine.cameraManager.camera = editorCamera.camera
        editorEventHandler.init()

        engine.jobSystem.runIO {
            settingsManager.loadSettings()
            engine.stringManager.loadStrings(EDITOR_STRINGS_EN)
            engine.stringManager.setLocale(settingsManager.editorSettings.language)
        }

        imGuiLayer.init(window.windowController)
    }

    fun update(dt: Float) {
        if (engine.sceneManager.currentScene?.isRunning == false) {
            editorInputHandler.update()
            editorCamera.update(dt)
            gizmoSystem.update(dt)
        }
        imGuiLayer.update(dt)
    }

    fun destroy() {
        imGuiLayer.destroy()
    }
}