package com.pafoid.skate.engine.scenes.editor

import com.pafoid.skate.engine.Transform
import com.pafoid.skate.engine.assets.*
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneInitializer
import com.pafoid.skate.engine.scenes.components.*
import com.pafoid.skate.engine.physics3d.components.*
import org.joml.Vector3f
import org.joml.Vector4f

class LevelEditorSceneInitializer: SceneInitializer() {

    private val loader = VAOLoader()

    private lateinit var rawModel: RawModel
    private lateinit var texture: Texture
    private lateinit var texturedModel: TexturedModel
    private lateinit var gizmoSprites: SpriteSheet
    
    private lateinit var editorStuff: GameObject
    lateinit var entity: Entity

    override fun loadResources(scene: Scene) {
        texture = AssetPool.getTexture(Texture.WHITE)
        
        AssetPool.addSpriteSheet("assets/textures/gizmos.png", 
            SpriteSheet(AssetPool.getTexture("assets/textures/gizmos.png"), 24, 48, 3, 0))
        gizmoSprites = AssetPool.getSpriteSheet("assets/textures/gizmos.png")!!

        rawModel = ObjLoader().loadObjModel(ObjLoader.SKATEBOARD, loader)
        texturedModel = TexturedModel(rawModel, texture)
        entity = Entity(
            model = texturedModel,
            transform = Transform(Vector3f(0f, 0f, -5f)),
            onTick = { /* entity.rotate(0f, 1f) */ }
        )
    }

    override fun init(scene: Scene) {
        // Level Editor Stuff
        editorStuff = scene.createGameObject("LevelEditor")
        editorStuff.setNoSerialize()
        editorStuff.addComponent(MouseControls())
        editorStuff.addComponent(GizmoSystem(gizmoSprites))
        editorStuff.addComponent(EditorCamera(scene.camera))
        scene.addGameObjectToScene(editorStuff)

        val skateboardGo = GameObject("skateboard")
        skateboardGo.addComponent(entity)
        
        val skateboardRb = RigidBody3D()
        skateboardRb.mass = 1f
        skateboardGo.addComponent(skateboardRb)
        
        // Deck
        val deckCollider = BoxCollider3D()
        deckCollider.halfExtents.set(1.5f, 0.05f, 0.5f)
        skateboardGo.addComponent(deckCollider)
        
        // Truck Front
        val truckFront = CylinderCollider3D(0.1f, 0.8f, 2) // Z-axis cylinder
        truckFront.offset.set(-1.0f, -0.1f, 0f)
        skateboardGo.addComponent(truckFront)
        
        // Truck Back
        val truckBack = CylinderCollider3D(0.1f, 0.8f, 2)
        truckBack.offset.set(1.0f, -0.1f, 0f)
        skateboardGo.addComponent(truckBack)
        
        skateboardGo.addComponent(PlayerController())
        
        scene.addGameObjectToScene(skateboardGo)

        // Ground
        val groundGo = GameObject("ground")
        groundGo.transform.translation.set(0f, -2f, -5f)
        groundGo.transform.scale.set(20f, 0.5f, 5f)
        
        val groundRb = RigidBody3D()
        groundRb.bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static
        groundGo.addComponent(groundRb)
        
        val groundCollider = BoxCollider3D()
        groundCollider.halfExtents.set(10f, 0.25f, 2.5f)
        groundGo.addComponent(groundCollider)
        groundGo.addComponent(Ground())
        
        scene.addGameObjectToScene(groundGo)

        // Rail
        val railGo = GameObject("rail")
        railGo.transform.translation.set(0f, -1.5f, -8f)
        railGo.transform.scale.set(10f, 0.1f, 0.1f)
        
        val railRb = RigidBody3D()
        railRb.bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static
        railGo.addComponent(railRb)
        
        val railCollider = CylinderCollider3D(0.1f, 10f, 0) // X-axis rail
        railGo.addComponent(railCollider)
        scene.addGameObjectToScene(railGo)

        // Kicker
        val kickerGo = GameObject("kicker")
        kickerGo.transform.translation.set(5f, -1.8f, -5f)
        kickerGo.transform.scale.set(2f, 0.5f, 2f)
        kickerGo.transform.rotation.set(0f, 0f, 20f) // 20 degree angle
        
        val kickerRb = RigidBody3D()
        kickerRb.bodyType = com.pafoid.skate.engine.physics3d.enums.BodyType.Static
        kickerGo.addComponent(kickerRb)
        
        val kickerCollider = BoxCollider3D()
        kickerCollider.halfExtents.set(1f, 0.25f, 1f)
        kickerGo.addComponent(kickerCollider)
        scene.addGameObjectToScene(kickerGo)
    }

    override fun imgui() {
        imgui.ImGui.begin("Level Editor")
        editorStuff.imgui()
        imgui.ImGui.end()

        imgui.ImGui.begin("Objects")
        if (imgui.ImGui.beginTabBar("WindowTabBar")) {
            if (imgui.ImGui.beginTabItem("Models")) {
                val objDir = java.io.File("assets/obj")
                val files = objDir.listFiles { _, name -> name.endsWith(".obj") || name.endsWith(".glb") || name.endsWith(".fbx") }
                
                files?.forEach { file ->
                    if (imgui.ImGui.button(file.name)) {
                        val model = AssetPool.getRawModel(file.path, loader)
                        val entityObj = com.pafoid.skate.engine.Prefabs.generateEntityObject(model, AssetPool.getTexture(Texture.WHITE), file.nameWithoutExtension)
                        editorStuff.getComponent<MouseControls>()?.pickUpObject(entityObj)
                    }
                }
                imgui.ImGui.endTabItem()
            }
            imgui.ImGui.endTabBar()
        }
        imgui.ImGui.end()
    }
}