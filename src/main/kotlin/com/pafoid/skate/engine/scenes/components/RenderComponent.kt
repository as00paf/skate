package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.models.BaseModel
import kotlinx.serialization.Serializable

enum class RenderMode {
    MESH,
    SKELETON,
    BOTH
}

@Serializable
class RenderComponent(
    val model: BaseModel,
    var shininess: Float = 10f,
    var reflectivity: Float = 1f,
    var textureScale: Float = 1.0f,
    var renderMode: RenderMode = RenderMode.MESH
) : Component() {

    override fun update(dt: Float) {
        // Render component doesn't need per-frame updates
    }
}