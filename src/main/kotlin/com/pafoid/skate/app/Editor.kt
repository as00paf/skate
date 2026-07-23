package com.pafoid.skate.app

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ConsoleActionHandler
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.editor.ui.handlers.EnvironmentActionHandler
import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.editor.ui.handlers.SceneActionHandler
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
    val mutationGate = EditorMutationGate(engine, engine.logger)

    val undoRedoManager = UndoRedoManager(mutationGate, engine.eventSystem, engine.logger)

    val editorInputHandler =
        EditorInputHandler(clipboardService, undoRedoManager, editorInputState, engine, projectManager)

    // TODO: regroup into one action handler
    val sceneActionHandler = SceneActionHandler(engine, projectManager, undoRedoManager, mutationGate)
    val projectActionHandler = ProjectActionHandler(engine, projectManager, undoRedoManager, stringManager)
    val environmentActionHandler = EnvironmentActionHandler(undoRedoManager, engine.eventSystem)
    val consoleActionHandler = ConsoleActionHandler(engine.eventSystem, engine.logger, undoRedoManager)

    val editorCamera = EditorCamera(Camera().also { it.position.set(Vector3f(0f, 5f, 20f)) }, editorInputState)
    val gizmoSystem = GizmoSystem(engine, settingsManager, undoRedoManager, editorCamera)
    val windowRegistry = WindowRegistry(
        engine,
        stringManager,
        clipboardService,
        settingsManager,
        undoRedoManager,
        projectManager,
        editorInputState,
        editorCamera,
        gizmoSystem,
        mutationGate
    )
    val imGuiLayer =
        ImGuiLayer(stringManager, engine, windowRegistry, projectManager, settingsManager, gizmoSystem)
    val editorEventHandler = EditorEventHandler(engine.eventSystem, imGuiLayer, undoRedoManager)

    fun update(dt: Float) {
        editorInputHandler.update()
        imGuiLayer.update(dt)
    }

    fun destroy() {
        imGuiLayer.destroy()
    }

    fun init(window: Window) {
        editorInputHandler.init(window.glfwWindow)
        editorEventHandler.init()
        imGuiLayer.init(window.windowController)
    }
}