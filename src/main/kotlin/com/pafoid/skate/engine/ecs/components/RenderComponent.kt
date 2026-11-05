package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.render.data.RenderMode
import com.pafoid.skate.engine.render.material.MaterialAsset
import kotlinx.serialization.Serializable
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.charset.StandardCharsets

@Serializable
data class RenderComponent(
    var model: `3dModel`? = null,
    /** Overrides the material of every mesh part; null renders the model's own part materials. */
    var material: Material? = null,
    var textureScale: Float = 1.0f,
    var renderMode: RenderMode = RenderMode.MESH,
    var castShadow: Boolean = true,
    var receiveShadow: Boolean = true,
    /** `.skmat` asset [material] comes from (EDT-17); reloaded from that file when the scene loads. */
    var materialPath: String? = null,
) : Component() {

    /**
     * Replaces [material] with the `.skmat` at [materialPath], reading it with [readText] and resolving its texture
     * paths with [resolveTexture]. Keeps the scene's inline copy and returns the error when the file can't be used.
     */
    fun resolveMaterialAsset(readText: (String) -> String?, resolveTexture: (String) -> Texture?): Throwable? {
        val path = materialPath ?: return null
        return runCatching {
            val text = readText(path) ?: throw java.io.FileNotFoundException(path)
            material = MaterialAsset.fromJson(text).toMaterial(resolveTexture)
        }.exceptionOrNull()
    }

    fun resolveModelFromPath(assetsManager: AssetsManager) {
        model = model?.path?.let { assetsManager.loadModel(it) }
        resolveMaterialAsset({ File(it).takeIf(File::isFile)?.readText() }, assetsManager::getTexture)
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
        material?.emissiveTexture?.let {
            it.texId = assetsManager.getTexture(it.filePath.orEmpty()).texId
        }
    }

    // TODO: move ?
    fun resolveModelFromByteArray(
        assetsManager: AssetsManager,
    ) {
        model?.path?.let { path ->
            model = assetsManager.resolveModel(path)
            resolveMaterialAsset(
                { p ->
                    assetsManager.assetsResolver.resolveData(p)?.let { data ->
                        try { StandardCharsets.UTF_8.decode(data).toString() } finally { MemoryUtil.memFree(data) }
                    }
                },
                assetsManager::resolveTexture,
            )
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
            material?.emissiveTexture?.let {
                it.texId = assetsManager.resolveTexture(it.filePath.orEmpty())?.texId ?: -1
            }
        }
    }

}
