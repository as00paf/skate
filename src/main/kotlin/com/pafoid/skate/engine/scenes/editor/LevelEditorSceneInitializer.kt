package com.pafoid.skate.engine.scenes.editor

import com.pafoid.skate.engine.animation.Animator
import com.pafoid.skate.engine.animation.PoseGizmo
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneInitializer
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.*
import com.pafoid.skate.engine.physics3d.components.*
import org.joml.Vector3f

class LevelEditorSceneInitializer: SceneInitializer() {
    private val loader = VAOLoader()
    private var currentScene: Scene? = null
    private lateinit var editorStuff: GameObject

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
        editorStuff = scene.createGameObject("EditorTools")
        editorStuff.setNoSerialize()
        editorStuff.addComponent(MouseControls())
        editorStuff.addComponent(GizmoSystem())
        editorStuff.addComponent(EditorCamera(scene.camera))
        editorStuff.addComponent(GridLines())
        editorStuff.addComponent(MeasureTool())
        scene.addGameObjectToScene(editorStuff)

        // TODO: Should be a prefab
        val skateGo = GameObject("Skateboard")
        skateGo.transform.translation.set(0f, 2f, 0f)
        skateGo.transform.scale.set(1.0f, 1.0f, 1.0f) // Now in Meters
        skateGo.addComponent(Entity(
            model = AssetPool.getModel(Assets.Models.SKATEBOARD_GLB, loader)
        ))
        skateGo.addComponent(RigidBody3D(1.8f).apply { friction = 0.1f }) // 1.8kg mass
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f))) // 0.8m x 0.04m x 0.2m
        skateGo.addComponent(SkateboardPhysics())
        skateGo.addComponent(PlayerController())
        skateGo.addComponent(TrickDetector())
        scene.addGameObjectToScene(skateGo)

        // TODO: Should be a prefab
        val playerGo = GameObject("Skater")
        // Parenting: Skater follows Skateboard
        skateGo.addChild(playerGo)
        
        // Position relative to board (standing on it)
        playerGo.transform.translation.set(0f, 0.05f, 0f) 
        playerGo.transform.rotation.set(0f, 90f, 0f) // Face sideways for skating
        playerGo.transform.scale.set(1.0f, 1.0f, 1.0f) // Now in Meters
        playerGo.addComponent(Entity(
            model = AssetPool.getModel(Assets.Models.JAMES, loader)
        ))
        playerGo.addComponent(Animator())
        playerGo.addComponent(PoseGizmo())
        scene.addGameObjectToScene(playerGo)

        // TODO: Should be a prefab
        val ground = GameObject("Floor")
        ground.transform.translation.set(0f, -0.5f, 0f)
        ground.transform.scale.set(100f, 0.5f, 100f)
        val groundTex = AssetPool.getTexture(Assets.Textures.ASPHALT)
        val groundModel = TexturedModel(AssetPool.getRawModel(Assets.Models.CUBE, loader), groundTex)
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