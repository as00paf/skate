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

    override fun loadResources(scene: Scene) {}

    override fun init(scene: Scene) {
        this.currentScene = scene

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

        // FEATURE 1: Basic Rendering (Cube)
        val cubeGo = GameObject("PhysicsCube")
        cubeGo.transform.translation.set(0f, 10f, 0f)
        cubeGo.addComponent(Entity(
            model = TexturedModel(AssetPool.getRawModel(ObjLoader.CUBE, loader), AssetPool.getTexture(Texture.WHITE))
        ))
        cubeGo.addComponent(RigidBody3D(1.0f))
        cubeGo.addComponent(BoxCollider3D(Vector3f(1f, 1f, 1f)))
        scene.addGameObjectToScene(cubeGo)

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
    }

    override fun imgui() {
        imgui.ImGui.begin("Skate Lab")
        imgui.ImGui.text("Feature 1: Basic Rendering [COMPLETE]")
        imgui.ImGui.text("Feature 2: Bullet Physics (Floor) [COMPLETE]")
        imgui.ImGui.text("Feature 3: Modular Tile System [COMPLETE]")
        imgui.ImGui.text("Feature 4: Skateboard Physics [PENDING]")

        if (imgui.ImGui.button("Reset Physics Cube")) {
            val scene = SceneManager.getCurrentScene()
            val cubeGo = scene?.gameObjects?.find { it.name == "PhysicsCube" }
            cubeGo?.let { go ->
                val rb = go.getComponent<RigidBody3D>()
                rb?.let { r ->
                    r.rawBody?.setPhysicsLocation(com.jme3.math.Vector3f(0f, 10f, 0f))
                    r.rawBody?.setPhysicsRotation(com.jme3.math.Quaternion.IDENTITY)
                    r.rawBody?.setLinearVelocity(com.jme3.math.Vector3f.ZERO)
                    r.rawBody?.setAngularVelocity(com.jme3.math.Vector3f.ZERO)
                    go.transform.translation.set(0f, 10f, 0f)
                    go.transform.rotation.set(0f, 0f, 0f)
                }
            }
        }

        imgui.ImGui.separator()
        imgui.ImGui.text("Spawn modular tiles and skateboards from the 'Prefabs' window.")

        imgui.ImGui.end()
    }
}
