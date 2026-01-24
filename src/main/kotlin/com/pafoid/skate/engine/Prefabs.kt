package com.pafoid.skate.engine

import com.pafoid.skate.engine.assets.Sprite
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import org.joml.Vector2f
import org.joml.Vector3f

object Prefabs {

    fun generateSpriteObject(sprite: Sprite, sizeX: Float, sizeY: Float, name: String = "Sprite_Object_Gen"): GameObject {
        val scene = SceneManager.getCurrentScene() ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)
        go.transform.scale.set(sizeX, sizeY, 1f)
        
        val renderer = SpriteRenderer()
        renderer.setSprite(sprite)
        go.addComponent(renderer)

        return go
    }

    fun generateEntityObject(model: RawModel, texture: Texture, name: String = "Entity_Object_Gen"): GameObject {
        val scene = SceneManager.getCurrentScene() ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)
        
        val texturedModel = TexturedModel(model, texture)
        val entity = Entity(texturedModel)
        go.addComponent(entity)
        
        // Link entity transform to game object transform for consistency
        entity.transform.copyFrom(go.transform)
        
        return go
    }
}