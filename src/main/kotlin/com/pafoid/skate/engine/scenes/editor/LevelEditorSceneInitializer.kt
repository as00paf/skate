package com.pafoid.skate.engine.scenes.editor

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneInitializer
import com.pafoid.skate.engine.scenes.components.*
import com.pafoid.skate.engine.physics3d.components.*
import com.pafoid.skate.engine.scenes.PrefabsGenerator
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LevelEditorSceneInitializer: SceneInitializer(), KoinComponent {
    private val resourceManager: ResourceManager by inject()
    private val prefabsGenerator: PrefabsGenerator by inject()

    private var currentScene: Scene? = null
    private lateinit var editorStuff: GameObject
    private lateinit var editorCamera: EditorCamera

    private var skateboard: GameObject? = null
    private var skater: GameObject? = null

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
        editorCamera = EditorCamera(scene.camera)
        editorStuff = scene.createGameObject("EditorTools")
        editorStuff.setNoSerialize()
        editorStuff.addComponent(MouseControls())
        editorStuff.addComponent(GizmoSystem())
        editorStuff.addComponent(editorCamera)
        editorStuff.addComponent(GridLines())
        editorStuff.addComponent(MeasureTool())
        scene.addGameObjectToScene(editorStuff)

        skateboard = prefabsGenerator.spawnSkateboard()
        skater = prefabsGenerator.spawnSkater(skateboard)

        // TODO: Should be a prefab
        val ground = GameObject("Floor")
        ground.transform.translation.set(0f, -0.5f, 0f)
        ground.transform.scale.set(100f, 0.5f, 100f)
        val groundTex = resourceManager.loadTextureSync(Assets.Textures.ASPHALT)
        val groundModel = TexturedModel(resourceManager.loadModelSync(Assets.Models.CUBE).parts[0].rawModel, groundTex)
        groundModel.parts[0].material.baseColorPath = Assets.Textures.ASPHALT
        
        ground.addComponent(Entity(
            model = groundModel,
            textureScale = 20.0f
        ))
        val groundRb = RigidBody3D(0f)
        groundRb.bodyType = BodyType.Static
        ground.addComponent(groundRb)
        ground.addComponent(BoxCollider3D(Vector3f(100f, 0.5f, 100f)))
        scene.addGameObjectToScene(ground)

        // Atmosphere (Spawned last for simple transparency sorting)
        val atmosphere = scene.createGameObject("Atmosphere")
        atmosphere.setNoSerialize()
        scene.addGameObjectToScene(atmosphere)
    }

    override fun imgui() {
    }
}