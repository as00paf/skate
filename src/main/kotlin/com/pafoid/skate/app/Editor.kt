package com.pafoid.skate.app

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.EditorActionHandler
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.render.Camera
import org.joml.Vector3f

class Editor(
    engine: Engine,
    stringManager: StringManager,
    projectManager: ProjectManager,
) {
    val settingsManager = SettingsManager(engine.serializer, engine.logger, stringManager)
    val clipboardService = ClipboardService(engine.serializer)
    val editorInputState = EditorInputState()

    val undoRedoManager = UndoRedoManager(engine.eventSystem, engine.logger)

    val editorInputHandler =
        EditorInputHandler(clipboardService, undoRedoManager, editorInputState, engine, settingsManager)

    val editorActionHandler = EditorActionHandler(engine, undoRedoManager, projectManager, stringManager)

    val editorCamera = EditorCamera(Camera().also { it.position.set(Vector3f(0f, 5f, 20f)) }, editorInputState)
    val gizmoSystem = GizmoSystem(engine, settingsManager, undoRedoManager, editorCamera)

    val imGuiLayer =
        ImGuiLayer(engine, this, stringManager, projectManager, settingsManager, gizmoSystem)
    val editorEventHandler = EditorEventHandler(engine.eventSystem, imGuiLayer, undoRedoManager)

    init {
        engine.cameraManager.camera = editorCamera.camera
    }

    fun update(dt: Float) {
        editorInputHandler.update()
        editorCamera.update(dt)
        imGuiLayer.update(dt)
    }

    fun destroy() {
        imGuiLayer.destroy()
    }

    fun init(window: Window) {
        editorEventHandler.init()
        imGuiLayer.init(window.windowController)
    }
}