package com.pafoid.skate.editor

import com.pafoid.skate.editor.systems.EditorInputHandler
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
    private val systemManager: SystemManager = SystemManager(),
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

        systemManager.update(dt)
        editorInputHandler.update(scene)
    }

    private fun initializeSystems(scene: Scene) {
        listOf(
            gameObjectManager,
            audioSystem, // Core
            inputSystem,
            environmentSystem,
            animationSystem,
            ragdollSystem,
            physicsSystem,
            directionalLightSystem,
            dayNightCycleSystem,
            editorCamera, // Editor
            gizmoSystem,
            gridLines,
        )
            .forEach {
            systemManager.addSystem(it)
            it.init(scene)
        }
    }

    fun destroy() {
        systemManager.destroy()
    }
}