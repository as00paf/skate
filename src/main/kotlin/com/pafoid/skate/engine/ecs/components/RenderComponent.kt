package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.render.data.RenderMode
import kotlinx.serialization.Serializable

@Serializable
data class RenderComponent(
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
        val baseColor = albedoTextureGuid.takeIf { it.isNotBlank() }?.let { assetsManager.getTexture(it) }
        val normalMap = normalMapGuid.takeIf { it.isNotBlank() }?.let { assetsManager.getTexture(it) }
        val metallicRoughness = metallicRoughnessGuid.takeIf { it.isNotBlank() }?.let { assetsManager.getTexture(it) }

        model?.let { model ->
            model.mesh.forEach { meshPart ->
                val mat = meshPart.material
                baseColor?.let { mat.baseColorTexture = it }
                normalMap?.let { mat.normalMap = it }
                metallicRoughness?.let { mat.metallicRoughnessTexture = it }
            }
        }
    }

    // TODO: move ?
    fun resolveModelFromByteArray(
        assetsManager: AssetsManager,
    ) {
        model?.path?.let { path ->
            model = assetsManager.resolveModel(path)
            val baseColor = albedoTextureGuid.takeIf { it.isNotBlank() }?.let { assetsManager.resolveTexture(it) }
            val normalMap = normalMapGuid.takeIf { it.isNotBlank() }?.let { assetsManager.resolveTexture(it) }
            val metallicRoughness =
                metallicRoughnessGuid.takeIf { it.isNotBlank() }?.let { assetsManager.resolveTexture(it) }

            model?.let { model ->
                model.mesh.forEach { meshPart ->
                    val mat = meshPart.material
                    baseColor?.let { mat.baseColorTexture = it }
                    normalMap?.let { mat.normalMap = it }
                    metallicRoughness?.let { mat.metallicRoughnessTexture = it }
                }
            }
        }
    }

}
