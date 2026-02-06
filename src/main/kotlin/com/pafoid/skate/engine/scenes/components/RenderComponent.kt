package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.models.CharacterModel
import kotlinx.serialization.Serializable

@Serializable
class RenderComponent(
    val model: CharacterModel,
    var shininess: Float = 10f,
    var reflectivity: Float = 1f,
    var textureScale: Float = 1.0f
) : Component() {

    override fun update(dt: Float) {
        // Render component doesn't need per-frame updates
    }
}