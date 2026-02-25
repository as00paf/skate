package com.pafoid.skate.editor

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.EditorInputStateComponent
import com.pafoid.skate.engine.ecs.config.DayNightCycleConfig
import com.pafoid.skate.engine.ecs.config.DirectionalLightConfig
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import com.pafoid.skate.engine.ecs.scene.addSystem
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.MouseControls
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.Renderer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LevelEditorSceneInitializer: SceneInitializer(), KoinComponent {
    private val prefabsGenerator: PrefabsGenerator by inject()
    private val resourceManager: ResourceManager by inject()
    private val logger: LoggerService by inject()
    private val sceneManager: SceneManager by inject()

    // Inject dependencies for systems
    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()
    private val serializer: Serializer by inject()
    private val settingsManager: SettingsManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val debugRenderer: DebugRenderer by inject()
    private val renderer: Renderer by inject()
    private val engine: Engine by inject()
    private val inputSystem: InputSystem by inject()

    private var currentScene: Scene? = null

    private var skateboard: GameObject? = null
    private var skater: GameObject? = null
    private var floor: List<GameObject?>? = null

    override suspend fun loadResources(scene: Scene) {
        reportProgress(0.1f, "Loading Character Model...")
        resourceManager.loadModel(Assets.Models.JAMES)
        reportProgress(0.1f, "Loading Skateboard Model...")
        resourceManager.loadModel(Assets.Models.SKATEBOARD_GLB)
        reportProgress(0.2f, "Resources Loaded.")
    }

    override suspend fun init(scene: Scene) {
        this.currentScene = scene

        reportProgress(0.3f, "Initializing Scene Data...")
        scene.sceneData.skyColor.set(0.6f, 0.7f, 0.9f)
        scene.sceneData.fogColor.set(0.6f, 0.7f, 0.9f) // Match sky for infinite horizon
        scene.sceneData.fogDensity = 0.0008f
        scene.sceneData.fogGradient = 0.8f

        // Set camera position
        scene.camera.position.set(0f, 5f, 20f)
        scene.camera.yaw = 0f

        reportProgress(0.5f, "Setting up Editor Tools...")

        // Essential Editor Tools as Systems
        scene.addSystem(inputSystem)
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
        scene.addSystem(GridLines(debugRenderer, sceneManager))
        scene.addSystem(AnimationSystem())

        reportProgress(0.7f, "Setting up Lighting Systems...")

        // Lighting Systems (must run after input systems)
        val dayNightCycleSystem = DayNightCycleSystem(DayNightCycleConfig().apply {
            cycleTime = scene.sceneData.timeOfDay
            dayDuration = 300f  // 5 minutes per day
        })
        scene.addSystem(dayNightCycleSystem)

        val directionalLightSystem = DirectionalLightSystem(DirectionalLightConfig().apply {
            direction.set(0f, -1f, 0f)  // Noon position
            color.set(1f, 0.95f, 0.8f)  // Warm sunlight
            intensity = 1f
            shadowDistance = 50f
            autoCalculateBounds = true
            stabilizeProjection = true
            depthBias = 0.005f
            slopeScaledBias = 0.01f
            castShadows = true
        })
        directionalLightSystem.setAutoAdjustBounds(true)  // Enable camera-based bounds adjustment
        scene.addSystem(directionalLightSystem)

        reportProgress(0.8f, "Spawning Prefabs...")
        //skateboard = prefabsGenerator.spawnSkateboard()
        skater = prefabsGenerator.spawnSkater(null)
        floor = prefabsGenerator.spawnFloor()

        reportProgress(1.0f, "Ready.")
    }

    override fun imgui() {
    }
}