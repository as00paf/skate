package com.pafoid.skate.engine.scenes.editor

import com.pafoid.skate.engine.Transform
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.assets.Sprite
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneInitializer
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import org.joml.Vector3f
import org.joml.Vector4f

class LevelEditorSceneInitializer: SceneInitializer() {

    private val loader = VAOLoader()

    private lateinit var rawModel: RawModel
    private lateinit var texture: Texture
    private lateinit var texturedModel: TexturedModel
    lateinit var entity: Entity

    override fun loadResources(scene: Scene) {
        texture = AssetPool.getTexture(Texture.WHITE)
        rawModel = ObjLoader().loadObjModel(ObjLoader.DRAGON, loader)
        texturedModel = TexturedModel(rawModel, texture)
        entity = Entity(
            model = texturedModel,
            transform = Transform(Vector3f(0f, 0f, -25f)),
            onTick = { entity.rotate(0f, 1f) }
        )
    }

    override fun init(scene: Scene) {
        // 3D Object
        val dragonGo = GameObject("dragon")
        dragonGo.addComponent(entity)
        scene.addGameObjectToScene(dragonGo)

        // 2D Object (Test Sprite)
        val spriteGo = GameObject("sprite_test")
        spriteGo.addComponent(Transform(Vector3f(-2f, 0f, -10f), Vector3f(1f, 1f, 1f)))
        val spriteRenderer = SpriteRenderer(color = Vector4f(1f, 0f, 0f, 1f)) // Red sprite
        val sprite = Sprite(texture)
        spriteRenderer.setSprite(sprite)
        spriteGo.addComponent(spriteRenderer)
        scene.addGameObjectToScene(spriteGo)
    }

    override fun imgui() {

    }
}