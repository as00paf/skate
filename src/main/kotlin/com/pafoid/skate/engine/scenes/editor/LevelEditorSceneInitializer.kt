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
import org.joml.Vector3f
import org.joml.Vector4f

class LevelEditorSceneInitializer: SceneInitializer() {

    private val loader = VAOLoader()

    private lateinit var rawModel: RawModel
    private lateinit var texture: Texture
    private lateinit var texturedModel: TexturedModel
    private lateinit var gizmoSprites: SpriteSheet
    lateinit var entity: Entity

    override fun loadResources(scene: Scene) {
        texture = AssetPool.getTexture(Texture.WHITE)
        AssetPool.addSpriteSheet("assets/textures/gizmos.png", 
            SpriteSheet(AssetPool.getTexture("assets/textures/gizmos.png"), 24, 48, 3, 0))
        gizmoSprites = AssetPool.getSpriteSheet("assets/textures/gizmos.png")!!
        
        rawModel = ObjLoader().loadObjModel(ObjLoader.CUBE, loader)
        texturedModel = TexturedModel(rawModel, texture)
        entity = Entity(
            model = texturedModel,
            transform = Transform(Vector3f(0f, 0f, -5f)),
            onTick = { /* entity.rotate(0f, 1f) */ }
        )
    }

    override fun init(scene: Scene) {
        // Level Editor Stuff
        val editorStuff = scene.createGameObject("LevelEditor")
        editorStuff.setNoSerialize()
        editorStuff.addComponent(MouseControls())
        editorStuff.addComponent(GizmoSystem(gizmoSprites))
        scene.addGameObjectToScene(editorStuff)

        // 3D Object
        val cubeGo = GameObject("cube")
        cubeGo.addComponent(entity)
        scene.addGameObjectToScene(cubeGo)

        // 2D Object (Test Sprite)
        val spriteGo = GameObject("sprite_test")
        spriteGo.addComponent(Transform(Vector3f(-2f, 0f, -2f), Vector3f(1f, 1f, 1f)))
        val spriteRenderer = SpriteRenderer(color = Vector4f(1f, 0f, 0f, 1f)) // Red sprite
        val sprite = Sprite(texture)
        spriteRenderer.setSprite(sprite)
        spriteGo.addComponent(spriteRenderer)
        scene.addGameObjectToScene(spriteGo)
    }

    override fun imgui() {

    }
}