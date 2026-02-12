package com.pafoid.skate.editor

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.MouseControls
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LevelEditorSceneInitializer: SceneInitializer(), KoinComponent {
    private val prefabsGenerator: PrefabsGenerator by inject()
    private val resourceManager: ResourceManager by inject()
    private val logger: LoggerService by inject()

    private var currentScene: Scene? = null
    private lateinit var editorStuff: GameObject

    private var editorCamera: GameObject? = null
    private var skateboard: GameObject? = null
    private var skater: GameObject? = null
    private var floor: GameObject? = null

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
        // Essential Editor Tools
        editorCamera = GameObject(EDITOR_CAMERA).apply {
            addComponent(EditorCamera(scene.camera))
            scene.addGameObjectToScene(this)
        }

        editorStuff = GameObject(EDITOR_TOOLS)
        editorStuff.setNoSerialize()
        editorStuff.addComponent(MouseControls())
        editorStuff.addComponent(GizmoSystem())
        editorStuff.addComponent(GridLines())
        editorStuff.addComponent(AnimationSystem())
        scene.addGameObjectToScene(editorStuff)

        reportProgress(0.7f, "Spawning Prefabs...")
        skateboard = prefabsGenerator.spawnSkateboard()
        skater = prefabsGenerator.spawnSkater(skateboard)
        floor = prefabsGenerator.spawnFloor()

        reportProgress(1.0f, "Ready.")
    }

    override fun imgui() {
    }

    companion object {
        const val EDITOR_CAMERA = "EditorCamera"
        const val EDITOR_TOOLS = "EditorTools"
    }
}