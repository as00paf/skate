package com.pafoid.skate.engine.core

import com.pafoid.skate.app.Workspace
import com.pafoid.skate.editor.EditorCamera
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
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
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback
import org.lwjgl.glfw.GLFW.glfwSetKeyCallback
import org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback
import org.lwjgl.glfw.GLFW.glfwSetScrollCallback

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
    private val editorInputHandler: EditorInputHandler
) : Workspace {

    val editorInputState: EditorInputStateComponent = EditorInputStateComponent()
    val systemManager: SystemManager = SystemManager()

    private var systemsInitialized = false

    private lateinit var editorCameraSystem: EditorCamera
    private lateinit var mouseControls: MouseControls
    private lateinit var gizmoSystem: GizmoSystem
    private lateinit var gridLines: GridLines

    override fun init(glfwWindow: Long) {
        glfwSetCursorPosCallback(glfwWindow, mouseListener::mousePosCallback)
        glfwSetMouseButtonCallback(glfwWindow, mouseListener::mouseButtonCallback)
        glfwSetScrollCallback(glfwWindow, mouseListener::mouseScrollCallback)
        glfwSetKeyCallback(glfwWindow, keyListener::keyCallback)
        joystickListener.init()
    }

    fun initSystems() {
        editorCameraSystem = EditorCamera(Camera(), editorInputState)
        mouseControls = MouseControls(keyListener, mouseListener, serializer, logger, renderer, engine, this)
        gizmoSystem = GizmoSystem(
            keyListener,
            mouseListener,
            settingsManager,
            undoRedoManager,
            renderer,
            engine,
            this,
            debugRenderer
        )
        gridLines = GridLines(debugRenderer, sceneManager, GridConfig(), stringManager)

        systemManager.addSystem(editorCameraSystem)
        systemManager.addSystem(mouseControls)
        systemManager.addSystem(gizmoSystem)
        systemManager.addSystem(gridLines)
    }

    override fun handleInputs() {
        joystickListener.update()

        // Record high-frequency input
        inputBuffer.push(
            Time.getTime(),
            Vector2f(mouseListener.getX(), mouseListener.getY()),
            joystickListener.getAxes(GLFW_JOYSTICK_1)
        )
        keyListener.endFrame()
        mouseListener.endFrame()
    }

    override fun update(dt: Float) {
        val scene = sceneManager.currentScene ?: return

        // Initialize editor systems with scene on first update
        // This is done lazily because systems are created before scene.startScene()
        if (!systemsInitialized) {
            initializeEditorSystems(scene)
            systemsInitialized = true
        }

        systemManager.editorUpdate(dt)
    }

    private fun initializeEditorSystems(scene: Scene) {
        // Editor systems need scene reference for their init()
        // But they don't store it permanently - they use it for setup
        editorCameraSystem.init(scene)
        mouseControls.init(scene)
        gizmoSystem.init(scene)
        gridLines.init(scene)

        // Start all editor systems
        systemManager.systems.forEach { system ->
            system.start()
        }
    }

    fun getSelectedGameObject(): GameObject? = _selectedGameObject


    fun setSelectedGameObject(gameObject: GameObject?) {
        _selectedGameObject = gameObject
    }

    private var _selectedGameObject: GameObject? = null

    fun getGizmoSystem(): GizmoSystem = gizmoSystem

    inline fun <reified T : System> getSystem(): T? {
        return systemManager.systems.filterIsInstance<T>().firstOrNull()
    }

    fun destroy() {
        systemManager.destroy()
        _selectedGameObject = null
    }
}
