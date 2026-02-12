package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.models.BaseModel
import com.pafoid.skate.engine.render.data.RenderMode
import kotlinx.serialization.Serializable

@Serializable
class RenderComponent(
    val model: BaseModel,
    var shininess: Float = 10f,
    var reflectivity: Float = 1f,
    var textureScale: Float = 1.0f,
    var renderMode: RenderMode = RenderMode.MESH
) : Component() {

}
