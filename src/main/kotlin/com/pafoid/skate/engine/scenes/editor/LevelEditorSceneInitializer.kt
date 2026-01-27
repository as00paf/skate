package com.pafoid.skate.engine.scenes.editor

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
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
        scene.addGameObjectToScene(editorStuff)

        // FEATURE 1: Basic Rendering (Skateboard)
        val skateGo = GameObject("Skateboard")
        skateGo.transform.translation.set(0f, 5f, 0f)
        skateGo.transform.scale.set(0.01f, 0.01f, 0.01f)
        skateGo.addComponent(Entity(
            model = AssetPool.getModel(ObjLoader.SKATEBOARD_GLB, loader)
        ))
        skateGo.addComponent(RigidBody3D(1.0f).apply { friction = 0.1f })
        skateGo.addComponent(BoxCollider3D(Vector3f(1.5f, 0.1f, 0.4f)))
        skateGo.addComponent(SkateboardPhysics())
        skateGo.addComponent(PlayerController())
        scene.addGameObjectToScene(skateGo)

        // FEATURE 1.1: Player Character (Skater)
        val playerGo = GameObject("Skater")
        // Parenting: Skater follows Skateboard
        skateGo.addChild(playerGo)
        
        // Position relative to board (standing on it)
        playerGo.transform.translation.set(0f, 0.1f, 0f) 
        playerGo.transform.scale.set(100f, 100f, 100f) // Scale back up to be "human" sized relative to the 0.01 board
        playerGo.addComponent(Entity(
            model = AssetPool.getModel(ObjLoader.PLAYER_GLTF, loader)
        ))
        scene.addGameObjectToScene(playerGo)

        // FEATURE 2: Bullet Physics (Floor)
        val ground = GameObject("Floor")
        ground.transform.translation.set(0f, -0.5f, 0f)
        ground.transform.scale.set(100f, 0.5f, 100f)
        ground.addComponent(Entity(
            model = TexturedModel(AssetPool.getRawModel(ObjLoader.CUBE, loader), AssetPool.getTexture(Texture.ASPHALT)),
            textureScale = 20.0f
        ))
        val groundRb = RigidBody3D(0f)
        groundRb.bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static
        ground.addComponent(groundRb)
        ground.addComponent(BoxCollider3D(Vector3f(100f, 0.5f, 100f)))
        scene.addGameObjectToScene(ground)

        // Atmosphere (Spawned last for simple transparency sorting)
        val atmosphere = scene.createGameObject("Atmosphere")
        atmosphere.setNoSerialize()
        scene.addGameObjectToScene(atmosphere)
    }

    override fun imgui() {
        imgui.ImGui.begin("Skate Lab")
        imgui.ImGui.text("Feature 1: Basic Rendering [COMPLETE]")
        imgui.ImGui.text("Feature 2: Bullet Physics (Floor) [COMPLETE]")
        imgui.ImGui.text("Feature 3: Modular Tile System [COMPLETE]")
        imgui.ImGui.text("Feature 4: Skateboard Physics [COMPLETE]")

        if (imgui.ImGui.button("Reset Skateboard")) {
            val scene = SceneManager.getCurrentScene()
            val skateGo = scene?.gameObjects?.find { it.name == "Skateboard" }
            skateGo?.let { go ->
                val rb = go.getComponent<RigidBody3D>()
                rb?.let { r ->
                    r.rawBody?.setPhysicsLocation(com.jme3.math.Vector3f(0f, 5f, 0f))
                    r.rawBody?.setPhysicsRotation(com.jme3.math.Quaternion.IDENTITY)
                    r.rawBody?.setLinearVelocity(com.jme3.math.Vector3f.ZERO)
                    r.rawBody?.setAngularVelocity(com.jme3.math.Vector3f.ZERO)
                    go.transform.translation.set(0f, 5f, 0f)
                    go.transform.rotation.set(0f, 0f, 0f)
                }
            }
        }

        imgui.ImGui.separator()
        imgui.ImGui.text("Spawn modular tiles and skateboards from the 'Prefabs' window.")

        imgui.ImGui.end()
    }
}