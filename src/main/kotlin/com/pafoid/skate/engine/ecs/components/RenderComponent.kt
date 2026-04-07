package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.models.BaseModel
import com.pafoid.skate.engine.render.data.RenderMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class RenderComponent(
    var modelGuid: String = "",
    @Transient var model: BaseModel? = null,
    var albedoTextureGuid: String = "",
    var normalMapGuid: String = "",
    var metallicRoughnessGuid: String = "",
    var aoGuid: String = "",
    var emissiveGuid: String = "",
    var shininess: Float = 10f,
    var reflectivity: Float = 1f,
    var textureScale: Float = 1.0f,
    var renderMode: RenderMode = RenderMode.MESH,
    var castShadow: Boolean = true,
    var receiveShadow: Boolean = true
) : Component() {

    /**
     * Legacy constructor for backward compatibility.
     * Use this when you have a direct model reference.
     */
    constructor(
        model: BaseModel,
        shininess: Float = 10f,
        reflectivity: Float = 1f,
        textureScale: Float = 1.0f,
        renderMode: RenderMode = RenderMode.MESH,
        castShadow: Boolean = true,
        receiveShadow: Boolean = true
    ) : this(
        modelGuid = "",
        model = model,
        shininess = shininess,
        reflectivity = reflectivity,
        textureScale = textureScale,
        renderMode = renderMode,
        castShadow = castShadow,
        receiveShadow = receiveShadow
    )

    override fun imgui() {
        super.imgui()
    }
}
