package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.render.data.RenderMode
import com.pafoid.skate.engine.utils.Atlas
import kotlinx.serialization.Serializable

@Serializable
class RenderComponent(
    var model: TexturedModel? = null,
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
        model = model?.path?.let { assetsManager.loadModel(it) }
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

    // TODO: textures
    fun resolveModelFromByteArray(
        assetsManager: AssetsManager,
        binData: ByteArray,
        assetsAtlas: Atlas,
        headerSize: Int = 0
    ) {
        model?.path?.let { path ->
            val extension = path.substring(path.lastIndexOf('.') + 1)
            val modelInfo = assetsAtlas[extension]?.firstOrNull {
                it.path == path
            }
            if (modelInfo != null) {
                val start = modelInfo.position
                val end = start + modelInfo.size
                if (end < binData.size) {
                    val modelData = binData.copyOfRange(start, end)
                    model = assetsManager.loadModel(modelData, path)
                }
            }
        }
    }

}
