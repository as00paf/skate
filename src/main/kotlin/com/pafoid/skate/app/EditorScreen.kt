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
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.utils.IJobSystem
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditorScreen(private val window: Window) : KoinComponent {

    private val engine: Engine by inject()
    private val logger: LoggerService by inject()
    private val eventSystem: EventSystem by inject()
    private val stringManager: StringManager by inject()
    private val jobSystem: IJobSystem by inject()

    private val clipboardService = ClipboardService(engine.serializer)
    private val editorInputState = EditorInputState()
    private val mutationGate = EditorMutationGate(engine, logger)
    private val prefabsGenerator = PrefabsGenerator(engine)

    private val undoRedoManager = UndoRedoManager(mutationGate, eventSystem, logger)
    private val settingsManager = SettingsManager(engine.serializer, logger, stringManager)
    private val projectManager = ProjectManager(engine, settingsManager, prefabsGenerator, eventSystem, logger)
    private val editorInputHandler = EditorInputHandler(
        clipboardService,
        undoRedoManager,
        logger,
        editorInputState,
        engine,
        projectManager,
        eventSystem
    )

    private val sceneActionHandler =
        SceneActionHandler(engine, projectManager, undoRedoManager, eventSystem, mutationGate, logger)
    private val projectActionHandler =
        ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem, jobSystem, stringManager)
    private val environmentActionHandler = EnvironmentActionHandler(undoRedoManager, eventSystem)
    private val consoleActionHandler = ConsoleActionHandler(eventSystem, logger, undoRedoManager)

    private val editorCamera = EditorCamera(Camera().also { it.position.set(Vector3f(0f, 5f, 20f)) }, editorInputState)
    private val gizmoSystem = GizmoSystem(settingsManager, undoRedoManager, engine, eventSystem, editorCamera)
    private val windowRegistry = WindowRegistry(
        engine,
        stringManager,
        clipboardService,
        eventSystem,
        settingsManager,
        undoRedoManager,
        projectManager,
        jobSystem,
        prefabsGenerator,
        editorInputState,
        editorCamera,
        gizmoSystem,
        mutationGate,
        logger
    )
    private val imGuiLayer =
        ImGuiLayer(stringManager, engine, windowRegistry, eventSystem, projectManager, settingsManager, gizmoSystem)
    private val editorEventHandler = EditorEventHandler(eventSystem, imGuiLayer, undoRedoManager)

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