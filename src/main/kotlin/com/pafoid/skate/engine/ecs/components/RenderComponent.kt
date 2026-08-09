package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.render.data.RenderMode
import kotlinx.serialization.Serializable

@Serializable
data class RenderComponent(
    var model: `3dModel`? = null,
    val material: Material? = null,
    var textureScale: Float = 1.0f,
    var renderMode: RenderMode = RenderMode.BOTH,
    var castShadow: Boolean = true,
    var receiveShadow: Boolean = true
) : Component() {

    fun resolveModelFromPath(assetsManager: AssetsManager) {
        model = model?.path?.let { assetsManager.loadModel(it) }
        material?.baseColorTexture?.let {
            it.texId = assetsManager.getTexture(it.filePath.orEmpty()).texId
        }
        material?.normalMap?.let {
            it.texId = assetsManager.getTexture(it.filePath.orEmpty()).texId
        }
        material?.metallicRoughnessTexture?.let {
            it.texId = assetsManager.getTexture(it.filePath.orEmpty()).texId
        }
        material?.aoTexture?.let {
            it.texId = assetsManager.getTexture(it.filePath.orEmpty()).texId
        }
    }

    // TODO: move ?
    fun resolveModelFromByteArray(
        assetsManager: AssetsManager,
    ) {
        model?.path?.let { path ->
            model = assetsManager.resolveModel(path)
            material?.baseColorTexture?.let {
                it.texId = assetsManager.resolveTexture(it.filePath.orEmpty())?.texId ?: -1
            }
            material?.normalMap?.let {
                it.texId = assetsManager.resolveTexture(it.filePath.orEmpty())?.texId ?: -1
            }
            material?.metallicRoughnessTexture?.let {
                it.texId = assetsManager.resolveTexture(it.filePath.orEmpty())?.texId ?: -1
            }
            material?.aoTexture?.let {
                it.texId = assetsManager.resolveTexture(it.filePath.orEmpty())?.texId ?: -1
            }
        }
    }

}
