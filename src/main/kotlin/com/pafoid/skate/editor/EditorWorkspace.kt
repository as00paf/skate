package com.pafoid.skate.editor

import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.core.Workspace
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.EditorInputStateComponent
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.GridConfig
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.renderer.DebugRenderer

class EditorWorkspace(
    private val gizmoSystem: GizmoSystem,
    private val debugRenderer: DebugRenderer,
    private val sceneManager: SceneManager,
    private val stringManager: StringManager,
    private val editorInputHandler: EditorInputHandler,
    private val editorEventHandler: EditorEventHandler,
) : Workspace {

    val editorInputState: EditorInputStateComponent = EditorInputStateComponent()
    val systemManager: SystemManager = SystemManager()

    private var systemsInitialized = false

    private val editorCamera: EditorCamera = EditorCamera(Camera(), editorInputState)
    private val gridLines: GridLines = GridLines(debugRenderer, sceneManager, GridConfig(), stringManager)

    override fun init(glfwWindow: Long) {
        editorInputHandler.init(glfwWindow)
        editorEventHandler.init()
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
        editorInputHandler.update(scene)
    }

    private fun initializeSystems(scene: Scene) {
        listOf(editorCamera, gizmoSystem, gridLines).forEach {
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