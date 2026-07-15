package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.render.data.RenderMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class RenderComponent(
    var modelPath: String = "",// TODO: fix duplicate
    @Transient var model: TexturedModel? = null,
    var albedoTextureGuid: String = "",
    var normalMapGuid: String = "",
    var metallicRoughnessGuid: String = "",
    var shininess: Float = 10f,
    var reflectivity: Float = 1f,
    var textureScale: Float = 1.0f,
    var renderMode: RenderMode = RenderMode.MESH,
    var castShadow: Boolean = true,
    var receiveShadow: Boolean = true
) : Component() {

    fun resolveModelFromPath(assetsManager: AssetsManager) {
        model = assetsManager.loadModelSync(modelPath)
        model?.let { model ->
            model.mesh.forEach { meshPart ->
                val mat = meshPart.material
                albedoTextureGuid.takeIf { it.isNotBlank() }
                    ?.let { mat.baseColorTexture = assetsManager.getTexture(it) }
                normalMapGuid.takeIf { it.isNotBlank() }?.let { mat.normalMap = assetsManager.getTexture(it) }
                metallicRoughnessGuid.takeIf { it.isNotBlank() }
                    ?.let { mat.metallicRoughnessTexture = assetsManager.getTexture(it) }
            }
        }
    }

}
