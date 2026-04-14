package com.pafoid.skate.engine.core

import com.pafoid.skate.editor.EditorCamera
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.EditorInputStateComponent
import com.pafoid.skate.engine.ecs.systems.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.GridConfig
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.MouseControls
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.Renderer

/**
 * EditorWorkspace owns editor-only systems and state that are independent of any specific game scene.
 *
 * ## Design Philosophy
 *
 * The editor is an application that hosts game scenes. They are not the same thing:
 * - **EditorWorkspace** = the application shell (camera, gizmos, grid, selection, editor input)
 * - **Scene** = a single game level (GameObjects, physics, gameplay systems, serialization unit)
 *
 * ## Ownership
 *
 * EditorWorkspace owns:
 * - Editor camera (Camera instance for editor navigation)
 * - Editor selection state (selectedGameObject)
 * - EditorInputStateComponent (editor input state)
 * - Editor systems: EditorCamera, MouseControls, GizmoSystem, GridLines
 *
 * Scene owns:
 * - All GameObjects
 * - Physics3D
 * - Gameplay systems: InputSystem, AnimationSystem, PhysicsSystem, AudioSystem, RagdollSystem,
 *   EnvironmentSystem, DayNightCycleSystem, DirectionalLightSystem
 *
 * ## Lifecycle
 *
 * EditorWorkspace is created AFTER Scene.init() completes. The boot sequence is:
 * 1. Scene created + init() called
 * 2. EditorWorkspace created
 * 3. Scene.startScene() called
 * 4. Engine enters update loop: workspace.editorUpdate(dt) then scene.editorUpdateScene(dt)
 *
 * ## Editor Systems and Scene
 *
 * Editor systems do NOT store Scene references in their init(). They receive Scene as a parameter
 * to their update() methods where needed. This keeps the workspace independent of any specific scene.
 */
class EditorWorkspace(
    private val keyListener: KeyListener,
    private val mouseListener: MouseListener,
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
) {

    /**
     * The editor camera for viewport navigation.
     */
    val camera: Camera = Camera()

    /**
     * Editor input state component populated by InputSystem.
     */
    val editorInputState: EditorInputStateComponent = EditorInputStateComponent()

    /**
     * SystemManager holding only editor systems.
     */
    val systemManager: SystemManager = SystemManager()

    /**
     * Editor systems owned by this workspace.
     */
    private lateinit var editorCameraSystem: EditorCamera
    private lateinit var mouseControls: MouseControls
    private lateinit var gizmoSystem: GizmoSystem
    private lateinit var gridLines: GridLines

    /**
     * Initializes the workspace by creating and registering editor systems.
     *
     * This should be called AFTER Scene.init() completes, but BEFORE Scene.startScene().
     */
    fun initSystems() {
        editorCameraSystem = EditorCamera(camera, editorInputState)
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

    /**
     * Updates all editor systems.
     *
     * Called by Engine every frame before scene.editorUpdateScene(dt).
     * Editor systems receive the current scene as a parameter where needed.
     *
     * @param dt Delta time
     * @param scene Current active scene (may be null during loading)
     */
    fun editorUpdate(dt: Float, scene: Scene?) {
        if (scene == null) return

        // Initialize editor systems with scene on first update
        // This is done lazily because systems are created before scene.startScene()
        if (!systemsInitialized) {
            initializeEditorSystems(scene)
            systemsInitialized = true
        }

        systemManager.editorUpdate(dt)
    }

    private var systemsInitialized = false

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

    /**
     * Gets the currently selected GameObject.
     */
    fun getSelectedGameObject(): GameObject? = _selectedGameObject

    /**
     * Sets the currently selected GameObject.
     */
    fun setSelectedGameObject(gameObject: GameObject?) {
        _selectedGameObject = gameObject
    }

    private var _selectedGameObject: GameObject? = null

    /**
     * Gets the EditorCamera system.
     */
    fun getEditorCamera(): EditorCamera = editorCameraSystem

    /**
     * Gets the MouseControls system.
     */
    fun getMouseControls(): MouseControls = mouseControls

    /**
     * Gets the GizmoSystem.
     */
    fun getGizmoSystem(): GizmoSystem = gizmoSystem

    /**
     * Gets the GridLines system.
     */
    fun getGridLines(): GridLines = gridLines

    /**
     * Gets an editor system by type.
     */
    inline fun <reified T : System> getSystem(): T? {
        return systemManager.systems.filterIsInstance<T>().firstOrNull()
    }

    /**
     * Destroys the workspace and all editor systems.
     */
    fun destroy() {
        systemManager.destroy()
        _selectedGameObject = null
    }
}
