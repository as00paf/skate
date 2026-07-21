package com.pafoid.skate.app

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.editor.systems.PrefabsGenerator
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

class EditorScreen(private val window: Window, private val engine: Engine) {

    private val stringManager = StringManager(engine.logger)

    private val clipboardService = ClipboardService(engine.serializer)
    private val editorInputState = EditorInputState()
    private val mutationGate = EditorMutationGate(engine, engine.logger)
    private val prefabsGenerator = PrefabsGenerator(engine)

    private val undoRedoManager = UndoRedoManager(mutationGate, engine.eventSystem, engine.logger)
    private val settingsManager = SettingsManager(engine.serializer, engine.logger, stringManager)
    private val projectManager =
        ProjectManager(engine, settingsManager, prefabsGenerator, engine.eventSystem, engine.logger)
    private val editorInputHandler =
        EditorInputHandler(clipboardService, undoRedoManager, editorInputState, engine, projectManager)

    private val sceneActionHandler = SceneActionHandler(engine, projectManager, undoRedoManager, mutationGate)
    private val projectActionHandler = ProjectActionHandler(engine, projectManager, undoRedoManager, stringManager)
    private val environmentActionHandler = EnvironmentActionHandler(undoRedoManager, engine.eventSystem)
    private val consoleActionHandler = ConsoleActionHandler(engine.eventSystem, engine.logger, undoRedoManager)

    private val editorCamera = EditorCamera(Camera().also { it.position.set(Vector3f(0f, 5f, 20f)) }, editorInputState)
    private val gizmoSystem = GizmoSystem(engine, settingsManager, undoRedoManager, editorCamera)
    private val windowRegistry = WindowRegistry(
        engine,
        stringManager,
        clipboardService,
        settingsManager,
        undoRedoManager,
        projectManager,
        prefabsGenerator,
        editorInputState,
        editorCamera,
        gizmoSystem,
        mutationGate
    )
    private val imGuiLayer =
        ImGuiLayer(stringManager, engine, windowRegistry, projectManager, settingsManager, gizmoSystem)
    private val editorEventHandler = EditorEventHandler(engine.eventSystem, imGuiLayer, undoRedoManager)

    fun init() {
        editorInputHandler.init(window.glfwWindow)
        editorEventHandler.init()

        settingsManager.load()
        imGuiLayer.init(window.windowController)
        projectManager.init()
    }

    fun update(dt: Float) {
        editorInputHandler.update()
        imGuiLayer.update(dt)
    }

    fun destroy() {
        imGuiLayer.destroy()
    }
}