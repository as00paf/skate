package com.pafoid.skate.editor

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.EditorInputStateComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.config.DayNightCycleConfig
import com.pafoid.skate.engine.ecs.config.DirectionalLightConfig
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import com.pafoid.skate.engine.ecs.scene.addSystem
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.GridConfig
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.MouseControls
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.RagdollSystem
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.Renderer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Thin orchestrator for editor scene initialization.
 * Delegates system creation to specialized components.
 * Does NOT spawn prefabs or save scenes — that is handled by BootManager after project load.
 */
class LevelEditorSceneInitializer : SceneInitializer(), KoinComponent {

    private val resourceManager: ResourceManager by inject()
    private val editorSystemFactory: EditorSystemFactory by inject()

    override suspend fun loadResources(scene: Scene) {
        reportProgress(0.1f, "Loading Character Model...")
        resourceManager.loadModel(Assets.Models.JAMES)
        reportProgress(0.1f, "Loading Skateboard Model...")
        resourceManager.loadModel(Assets.Models.SKATEBOARD_GLB)
        reportProgress(0.2f, "Resources Loaded.")
    }

    override suspend fun init(scene: Scene) {
        reportProgress(0.3f, "Initializing Scene Data...")

        scene.camera.position.set(0f, 5f, 20f)
        scene.camera.yaw = 0f

        reportProgress(0.5f, "Setting up Editor Tools...")

        editorSystemFactory.addEditorSystems(scene)

        scene.addComponent(EnvironmentComponent())
        scene.addComponent(TimeComponent(timeOfDay = 12.0f, timeScale = 1.0f))
        scene.addComponent(LightingStateComponent())

        reportProgress(0.7f, "Setting up Lighting Systems...")

        editorSystemFactory.addLightingSystems(scene)

        reportProgress(1.0f, "Ready.")
    }

    override fun imgui() {
    }
}

/**
 * Factory for creating and adding editor systems to a scene.
 *
 * Encapsulates all system dependencies and construction logic,
 * keeping the scene initializer clean and focused on orchestration.
 */
class EditorSystemFactory : KoinComponent {
    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()
    private val serializer: Serializer by inject()
    private val settingsManager: SettingsManager by inject()
    private val stringManager: StringManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val debugRenderer: DebugRenderer by inject()
    private val renderer: Renderer by inject()
    private val engine: Engine by inject()
    private val inputProvider: IInputProvider by inject()
    private val audioEngine: AudioEngine by inject()
    private val sceneManager: SceneManager by inject()
    private val logger: LoggerService by inject()

    /**
     * Add all editor input, gameplay, and utility systems to the scene.
     */
    fun addEditorSystems(scene: Scene) {
        val inputSystem = InputSystem(inputProvider, mouseListener, settingsManager, stringManager)
        scene.addSystem(inputSystem)
        scene.addSystem(EventSystem())
        scene.addSystem(EditorCamera(scene.camera, EditorInputStateComponent()))
        scene.addSystem(MouseControls(keyListener, mouseListener, serializer, logger, renderer, engine))
        scene.addSystem(
            GizmoSystem(
                keyListener,
                mouseListener,
                settingsManager,
                undoRedoManager,
                renderer,
                engine,
                debugRenderer,
            )
        )
        scene.addSystem(GridLines(debugRenderer, sceneManager, GridConfig(), stringManager))
        scene.addSystem(AnimationSystem(stringManager))
        scene.addSystem(AudioSystem(audioEngine, logger))
        scene.addSystem(EnvironmentSystem(stringManager = stringManager))
        scene.addSystem(PhysicsSystem())
        scene.addSystem(RagdollSystem())
    }

    /**
     * Add lighting and day/night cycle systems to the scene.
     */
    fun addLightingSystems(scene: Scene) {
        val dayNightCycleSystem = DayNightCycleSystem(
            DayNightCycleConfig().apply {
                cycleTime = scene.getTimeOfDay()
                dayDuration = 300f
            },
            stringManager = stringManager
        )
        scene.addSystem(dayNightCycleSystem)

        val directionalLightSystem = DirectionalLightSystem(
            DirectionalLightConfig().apply {
                direction.set(0f, -1f, 0f)
                color.set(1f, 0.95f, 0.8f)
                intensity = 1f
                shadowDistance = 50f
                autoCalculateBounds = true
                stabilizeProjection = true
                depthBias = 0.0f
                slopeScaledBias = 0.0f
                castShadows = true
            },
            stringManager = stringManager
        )
        directionalLightSystem.setAutoAdjustBounds(true)
        scene.addSystem(directionalLightSystem)
    }
}
