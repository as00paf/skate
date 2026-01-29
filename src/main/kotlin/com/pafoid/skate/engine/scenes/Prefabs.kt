package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Sprite
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.components.ModularTile
import com.pafoid.skate.engine.scenes.components.SpriteRenderer

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

    fun generateTileObject(sizeX: Float, sizeY: Float, texture: Texture, name: String = "Tile_Gen"): GameObject {
        val scene = SceneManager.getCurrentScene() ?: throw IllegalStateException("No active scene")
        val go = scene.createGameObject(name)

        val tile = ModularTile()
        tile.size.set(sizeX, sizeY, 1f)
        go.addComponent(tile)

        // We use a cube as the base model for tiles
        val loader = VAOLoader() // Inefficient to create here, but for now...
        val cubeModel = AssetPool.getRawModel(Assets.Models.CUBE, loader)

        val texturedModel = TexturedModel(cubeModel, texture)
        val entity = Entity(texturedModel)
        go.addComponent(entity)

        go.transform.scale.set(sizeX, sizeY, 1f)
        entity.transform.copyFrom(go.transform)

        return go
    }
}