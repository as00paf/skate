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
    var renderMode: RenderMode = RenderMode.MESH,
    var castShadow: Boolean = true,
    var receiveShadow: Boolean = true
) : Component() {

    fun resolveModelFromPath(assetsManager: AssetsManager) {
        model = model?.path?.let { assetsManager.loadModel(it) }
        val baseColor =
            material?.baseColorTexture?.filePath?.takeIf { it.isNotBlank() }?.let { assetsManager.getTexture(it) }
        val normalMap = material?.normalMap?.filePath?.takeIf { it.isNotBlank() }?.let { assetsManager.getTexture(it) }
        val metallicRoughness = material?.metallicRoughnessTexture?.filePath?.takeIf { it.isNotBlank() }
            ?.let { assetsManager.getTexture(it) }
        val ambientOcclusionMap =
            material?.aoTexture?.filePath?.takeIf { it.isNotBlank() }?.let { assetsManager.getTexture(it) }

        model?.let { model ->
            model.mesh.forEach { meshPart ->
                val mat = meshPart.material
                baseColor?.let { mat.baseColorTexture = it }
                normalMap?.let { mat.normalMap = it }
                metallicRoughness?.let { mat.metallicRoughnessTexture = it }
                ambientOcclusionMap?.let { mat.aoTexture = it }
            }
        }
    }

    // TODO: move ?
    fun resolveModelFromByteArray(
        assetsManager: AssetsManager,
    ) {
        model?.path?.let { path ->
            model = assetsManager.resolveModel(path)
            val baseColor = material?.baseColorTexture?.filePath?.takeIf { it.isNotBlank() }
                ?.let { assetsManager.resolveTexture(it) }
            val normalMap =
                material?.normalMap?.filePath?.takeIf { it.isNotBlank() }?.let { assetsManager.resolveTexture(it) }
            val metallicRoughness =
                material?.metallicRoughnessTexture?.filePath?.takeIf { it.isNotBlank() }
                    ?.let { assetsManager.resolveTexture(it) }
            val ambientOcclusionMap =
                material?.aoTexture?.filePath?.takeIf { it.isNotBlank() }?.let { assetsManager.resolveTexture(it) }

            model?.let { model ->
                model.mesh.forEach { meshPart ->
                    val mat = meshPart.material
                    baseColor?.let { mat.baseColorTexture = it }
                    normalMap?.let { mat.normalMap = it }
                    metallicRoughness?.let { mat.metallicRoughnessTexture = it }
                    ambientOcclusionMap?.let { mat.aoTexture = it }
                }
            }
        }
    }

}
