package com.pafoid.skate.editor

import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.engine.core.Workspace
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.RagdollSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager

class EditorWorkspace(
    private val systemManager: SystemManager,
    private val sceneManager: SceneManager,
    private val gameObjectManager: GameObjectManager,
    private val gizmoSystem: GizmoSystem,
    private val gridLines: GridLines,
    private val editorInputHandler: EditorInputHandler,
    private val editorEventHandler: EditorEventHandler,
    private val audioSystem: AudioSystem,
    private val inputSystem: InputSystem,
    private val animationSystem: AnimationSystem,
    private val physicsSystem: PhysicsSystem,
    private val ragdollSystem: RagdollSystem,
    private val dayNightCycleSystem: DayNightCycleSystem,
    private val environmentSystem: EnvironmentSystem,
    private val directionalLightSystem: DirectionalLightSystem,
    private val editorCamera: EditorCamera,
) : Workspace {

    private var systemsInitialized = false
    private var activeSystemScene: Scene? = null

    override fun init(glfwWindow: Long) {
        editorInputHandler.init(glfwWindow)
        editorEventHandler.init()
    }

    override fun update(dt: Float) {
        val scene = sceneManager.currentScene ?: return

        if (!systemsInitialized) {
            initializeSystems()
            systemsInitialized = true
        }

        if (activeSystemScene !== scene) {
            systemManager.loadScene(scene)
            activeSystemScene = scene
        }

        if (!systemManagerStarted) {
            systemManager.start()
            systemManagerStarted = true
        }

        systemManager.update(dt)
    }

    private var systemManagerStarted = false

    private fun initializeSystems() {
        listOf(
            gameObjectManager, // Core
            inputSystem,
            audioSystem,
            environmentSystem, // Engine
            physicsSystem,
            dayNightCycleSystem,
            directionalLightSystem,
            animationSystem,
            ragdollSystem,
            editorInputHandler, // Editor
            editorCamera,
            gizmoSystem,
            gridLines,
        ).forEach {
            systemManager.addSystem(it)
        }
    }

    fun destroy() {
        systemManager.destroy()
        systemsInitialized = false
        systemManagerStarted = false
        activeSystemScene = null
    }
}
