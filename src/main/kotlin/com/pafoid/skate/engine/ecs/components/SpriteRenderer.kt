package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.core.LoggerService
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector4f

@Serializable
data class SpriteRenderer(
    @Contextual val color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    @Contextual var sprite: Sprite = Sprite(),
    var zIndex: Int = 0
): Component() {

    fun resolveTextureFromPaths(assetsManager: AssetsManager, logger: LoggerService) {
        sprite.texture?.filePath.orEmpty().takeIf { it.isNotBlank() }
            ?.let { sprite.texture = assetsManager.getTexture(it) }
    }

}