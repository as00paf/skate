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
        editorStuff.addComponent(GridLines())
        editorStuff.addComponent(MeasureTool())
        scene.addGameObjectToScene(editorStuff)

        val skateGo = GameObject("Skateboard")
        skateGo.transform.translation.set(0f, 2f, 0f)
        skateGo.transform.scale.set(1.0f, 1.0f, 1.0f) // Now in Meters
        skateGo.addComponent(Entity(
            model = AssetPool.getModel(ObjLoader.SKATEBOARD_GLB, loader)
        ))
        skateGo.addComponent(RigidBody3D(1.8f).apply { friction = 0.1f }) // 1.8kg mass
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f))) // 0.8m x 0.04m x 0.2m
        skateGo.addComponent(SkateboardPhysics())
        skateGo.addComponent(PlayerController())
        scene.addGameObjectToScene(skateGo)


        val playerGo = GameObject("Skater")
        // Parenting: Skater follows Skateboard
        skateGo.addChild(playerGo)
        
        // Position relative to board (standing on it)
        playerGo.transform.translation.set(0f, 0.05f, 0f) 
        playerGo.transform.rotation.set(0f, 90f, 0f) // Face sideways for skating
        playerGo.transform.scale.set(1.0f, 1.0f, 1.0f) // Now in Meters
        playerGo.addComponent(Entity(
            model = AssetPool.getModel(ObjLoader.JAMES, loader)
        ))
        playerGo.addComponent(com.pafoid.skate.engine.animation.Animator())
        scene.addGameObjectToScene(playerGo)

        val ground = GameObject("Floor")
        ground.transform.translation.set(0f, -0.5f, 0f)
        ground.transform.scale.set(100f, 0.5f, 100f)
        val groundTex = AssetPool.getTexture(Texture.ASPHALT)
        val groundModel = TexturedModel(AssetPool.getRawModel(ObjLoader.CUBE, loader), groundTex)
        groundModel.parts[0].material.baseColorPath = Texture.ASPHALT
        
        ground.addComponent(Entity(
            model = groundModel,
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
        imgui.ImGui.text("Phase D: Task 10.1 - Proportions")
        
        val scene = SceneManager.getCurrentScene()
        val skateGo = scene?.gameObjects?.find { it.name == "Skateboard" }
        val skaterGo = scene?.gameObjects?.find { it.name == "Skater" }
        
        skateGo?.let { go ->
            val collider = go.getComponent<BoxCollider3D>()
            val length = (collider?.halfExtents?.x ?: 0f) * 2f
            imgui.ImGui.text("Board Length: ${String.format("%.2f", length)}m (Target: 0.80m)")
        }
        
        skaterGo?.let { go ->
            // The model height is now baked into vertices at ~1.8m via AssimpLoader
            val height = go.transform.scale.y * 1.8f 
            imgui.ImGui.text("World Skater Height: ${String.format("%.2f", height)}m (Target: 1.80m)")
            imgui.ImGui.text("Skater Object Scale: ${String.format("%.2f", go.transform.scale.y)}")
        }

        imgui.ImGui.separator()
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
                    r.rawBody?.setPhysicsLocation(com.jme3.math.Vector3f(0f, 2f, 0f))
                    r.rawBody?.setPhysicsRotation(com.jme3.math.Quaternion.IDENTITY)
                    r.rawBody?.setLinearVelocity(com.jme3.math.Vector3f.ZERO)
                    r.rawBody?.setAngularVelocity(com.jme3.math.Vector3f.ZERO)
                    go.transform.translation.set(0f, 2f, 0f)
                    go.transform.rotation.set(0f, 0f, 0f)
                }
            }
        }

        imgui.ImGui.separator()
        imgui.ImGui.text("Spawn modular tiles and skateboards from the 'Prefabs' window.")

        imgui.ImGui.end()
    }
}