package com.pafoid.skate.engine.scenes.editor

import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneInitializer
import com.pafoid.skate.engine.scenes.components.*
import com.pafoid.skate.engine.prefabs.PrefabsGenerator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LevelEditorSceneInitializer: SceneInitializer(), KoinComponent {
    private val prefabsGenerator: PrefabsGenerator by inject()

    private var currentScene: Scene? = null
    private lateinit var editorStuff: GameObject

    private var editorCamera: GameObject? = null
    private var skateboard: GameObject? = null
    private var skater: GameObject? = null
    private var floor: GameObject? = null

    override suspend fun loadResources(scene: Scene) {}

    override suspend fun init(scene: Scene) {
        this.currentScene = scene

        scene.skyColor.set(0.6f, 0.7f, 0.9f)
        scene.fogColor.set(0.6f, 0.7f, 0.9f) // Match sky for infinite horizon
        scene.fogDensity = 0.0008f
        scene.fogGradient = 0.8f

        // Set camera position
        scene.camera.position.set(0f, 5f, 20f)
        scene.camera.yaw = 0f
        
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
        scene.addGameObjectToScene(editorStuff)

        skateboard = prefabsGenerator.spawnSkateboard()
        skater = prefabsGenerator.spawnSkater(skateboard)
        floor = prefabsGenerator.spawnFloor()
    }

    override fun imgui() {
    }

    companion object {
        const val EDITOR_CAMERA = "EditorCamera"
        const val EDITOR_TOOLS = "EditorTools"
    }
}