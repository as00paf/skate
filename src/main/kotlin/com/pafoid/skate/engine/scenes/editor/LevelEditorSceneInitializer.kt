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
import com.pafoid.skate.engine.physics2d.components.*
import org.joml.Vector3f
import org.joml.Vector4f

class LevelEditorSceneInitializer: SceneInitializer() {

    private val loader = VAOLoader()

    private lateinit var rawModel: RawModel
    private lateinit var texture: Texture
    private lateinit var texturedModel: TexturedModel
    private lateinit var gizmoSprites: SpriteSheet
    private lateinit var blocksSprites: SpriteSheet
    private lateinit var peterSprites: SpriteSheet
    
    private lateinit var editorStuff: GameObject
    lateinit var entity: Entity

    override fun loadResources(scene: Scene) {
        texture = AssetPool.getTexture(Texture.WHITE)
        
        AssetPool.addSpriteSheet("assets/textures/gizmos.png", 
            SpriteSheet(AssetPool.getTexture("assets/textures/gizmos.png"), 24, 48, 3, 0))
        gizmoSprites = AssetPool.getSpriteSheet("assets/textures/gizmos.png")!!

        AssetPool.addSpriteSheet("assets/textures/blocksAndDecorations.png",
            SpriteSheet(AssetPool.getTexture("assets/textures/blocksAndDecorations.png"), 32, 32, 14, 0))
        blocksSprites = AssetPool.getSpriteSheet("assets/textures/blocksAndDecorations.png")!!

        AssetPool.addSpriteSheet("assets/textures/peter_sprite.png",
            SpriteSheet(AssetPool.getTexture("assets/textures/peter_sprite.png"), 100, 100, 26, 0))
        peterSprites = AssetPool.getSpriteSheet("assets/textures/peter_sprite.png")!!
        
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
        scene.addGameObjectToScene(editorStuff)

        // 3D Object (Skateboard)
        val skateboardGo = GameObject("skateboard")
        skateboardGo.addComponent(entity)
        
        val rb = RigidBody2D()
        rb.mass = 1f
        rb.friction = 0.5f
        skateboardGo.addComponent(rb)
        
        val collider = Box2DCollider()
        collider.halfSize.set(1.5f, 0.25f) // Roughly deck size
        skateboardGo.addComponent(collider)
        
        skateboardGo.addComponent(PlayerController())
        
        scene.addGameObjectToScene(skateboardGo)

        // Ground
        val groundGo = GameObject("ground")
        groundGo.transform.translation.set(0f, -2f, -5f)
        groundGo.transform.scale.set(20f, 0.5f, 5f)
        
        val groundRb = RigidBody2D()
        groundRb.bodyType = com.pafoid.skate.engine.physics2d.enums.BodyType.Static
        groundGo.addComponent(groundRb)
        
        val groundCollider = Box2DCollider()
        groundCollider.halfSize.set(10f, 0.25f)
        groundGo.addComponent(groundCollider)
        groundGo.addComponent(Ground())
        
        scene.addGameObjectToScene(groundGo)
    }

    override fun imgui() {
        imgui.ImGui.begin("Level Editor")
        editorStuff.imgui()
        imgui.ImGui.end()

        imgui.ImGui.begin("Objects")
        if (imgui.ImGui.beginTabBar("WindowTabBar")) {
            if (imgui.ImGui.beginTabItem("Blocks")) {
                val windowSize = imgui.ImVec2()
                imgui.ImGui.getContentRegionAvail(windowSize)
                val itemSpacing = imgui.ImVec2()
                imgui.ImGui.getStyle().getItemSpacing(itemSpacing)
                
                for (i in 0 until blocksSprites.size()) {
                    val sprite = blocksSprites.getSprite(i)
                    val id = sprite.getTexId()
                    val texCoords = sprite.getTexCoords()

                    imgui.ImGui.pushID(i)
                    if (imgui.ImGui.imageButton("BlockButton_$i", id.toLong(), 32f, 32f, texCoords[2].x, texCoords[0].y, texCoords[0].x, texCoords[2].y)) {
                        val block = com.pafoid.skate.engine.Prefabs.generateSpriteObject(sprite, 0.25f, 0.25f, "Block_$i")
                        editorStuff.getComponent<MouseControls>()?.pickUpObject(block)
                    }
                    imgui.ImGui.popID()

                    val lastButtonPos = imgui.ImVec2()
                    imgui.ImGui.getItemRectMax(lastButtonPos)
                    val lastButtonX2 = lastButtonPos.x
                    val nextButtonX2 = lastButtonX2 + itemSpacing.x + 32f
                    if (i + 1 < blocksSprites.size() && nextButtonX2 < imgui.ImGui.getWindowPosX() + windowSize.x) {
                        imgui.ImGui.sameLine()
                    }
                }
                imgui.ImGui.endTabItem()
            }
            
            if (imgui.ImGui.beginTabItem("Prefabs")) {
                val sprite = peterSprites.getSprite(0)
                val texCoords = sprite.getTexCoords()
                if (imgui.ImGui.imageButton("PeterButton", sprite.getTexId().toLong(), 32f, 32f, texCoords[2].x, texCoords[0].y, texCoords[0].x, texCoords[2].y)) {
                    val peter = com.pafoid.skate.engine.Prefabs.generateSpriteObject(sprite, 0.25f, 0.25f, "Peter")
                    editorStuff.getComponent<MouseControls>()?.pickUpObject(peter)
                }
                imgui.ImGui.endTabItem()
            }
            imgui.ImGui.endTabBar()
        }
        imgui.ImGui.end()
    }
}