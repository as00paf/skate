package com.pafoid.skate.editor

import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.Workspace
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.EditorInputStateComponent
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.GridConfig
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.MouseControls
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.input.IInputBuffer
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.Time
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW

class EditorWorkspace(
    val keyListener: KeyListener,
    val mouseListener: MouseListener,
    val joystickListener: GamepadListener,
    val inputBuffer: IInputBuffer,
    private val serializer: Serializer,
    private val settingsManager: SettingsManager,
    private val undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
    private val renderer: Renderer,
    private val engine: Engine,
    private val sceneManager: SceneManager,
    private val logger: LoggerService,
    private val stringManager: StringManager,
    private val editorInputHandler: EditorInputHandler,
    private val editorEventHandler: EditorEventHandler,
    private val eventSystem: EventSystem
) : Workspace {

    val editorInputState: EditorInputStateComponent = EditorInputStateComponent()
    val systemManager: SystemManager = SystemManager()

    private var systemsInitialized = false

    private val editorCamera: EditorCamera = EditorCamera(Camera(), editorInputState)
    private val mouseControls: MouseControls =
        MouseControls(keyListener, mouseListener, serializer, logger, renderer, engine, eventSystem)
    private val gizmoSystem: GizmoSystem = GizmoSystem(
        keyListener,
        mouseListener,
        settingsManager,
        undoRedoManager,
        renderer,
        engine,
        sceneManager,
        debugRenderer
    )
    private val gridLines: GridLines = GridLines(debugRenderer, sceneManager, GridConfig(), stringManager)

    override fun init(glfwWindow: Long) {
        GLFW.glfwSetCursorPosCallback(glfwWindow, mouseListener::mousePosCallback)
        GLFW.glfwSetMouseButtonCallback(glfwWindow, mouseListener::mouseButtonCallback)
        GLFW.glfwSetScrollCallback(glfwWindow, mouseListener::mouseScrollCallback)
        GLFW.glfwSetKeyCallback(glfwWindow, keyListener::keyCallback)
        joystickListener.init()

        editorEventHandler.init()
    }

    override fun handleInputs() {
        joystickListener.update()

        // Record high-frequency input
        inputBuffer.push(
            Time.getTime(),
            Vector2f(mouseListener.getX(), mouseListener.getY()),
            joystickListener.getAxes(GLFW.GLFW_JOYSTICK_1)
        )
        keyListener.endFrame()
        mouseListener.endFrame()
    }

    override fun update(dt: Float) {
        val scene = sceneManager.currentScene ?: return

        // Initialize editor systems with scene on first update
        // This is done lazily because systems are created before scene.startScene()
        if (!systemsInitialized) {
            initializeSystems(scene)
            systemsInitialized = true
        }

        systemManager.editorUpdate(dt)
    }

    private fun initializeSystems(scene: Scene) {
        listOf(editorCamera, mouseControls, gizmoSystem, gridLines).forEach {
            systemManager.addSystem(it)
            it.init(scene)
        }
    }

    fun getGizmoSystem(): GizmoSystem = gizmoSystem

    inline fun <reified T : System> getSystem(): T? {
        return systemManager.systems.filterIsInstance<T>().firstOrNull()
    }

    fun destroy() {
        systemManager.destroy()
    }
}