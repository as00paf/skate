package com.pafoid.skate.engine

import com.pafoid.skate.engine.assets.Sprite
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
}