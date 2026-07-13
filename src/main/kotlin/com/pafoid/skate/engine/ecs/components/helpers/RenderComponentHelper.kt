package com.pafoid.skate.engine.ecs.components.helpers

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.ecs.components.RenderComponent
import java.io.File

/**
 * Helper for safely assigning models and textures to RenderComponent with immediate GUID resolution.
 * Ensures that modelGuid and texture GUIDs are always populated when assets are assigned.
 */
class RenderComponentHelper(
    private val assetDatabase: AssetDatabase,
    private val logger: LoggerService
) {

    /**
     * Assigns a model to a RenderComponent and immediately resolves its GUID.
     */
    fun setModelWithGuid(
        renderComponent: RenderComponent,
        model: TexturedModel
    ) {
        renderComponent.model = model

        // Resolve and set modelGuid
        val absolutePath = File(model.path).absolutePath

        val asset = assetDatabase.getByAbsolutePath(absolutePath)
        renderComponent.modelGuid = asset?.guid?.value ?: absolutePath

        if (asset != null) {
            logger.log("Resolved model GUID for '${absolutePath}': ${asset.guid.value.take(8)}", LogLevel.INFO)
        } else {
            logger.log(
                "WARNING: Model not found in AssetDatabase, using absolute path: $absolutePath",
                LogLevel.WARN
            )
        }
    }

    /**
     * Assigns a texture to a material and updates the corresponding GUID in RenderComponent.
     */
    fun setAlbedoTextureWithGuid(
        renderComponent: RenderComponent,
        material: Material,
        texture: Texture
    ) {
        material.baseColorTexture = texture
        resolveTextureGuid(texture, renderComponent) { rc, guid ->
            rc.albedoTextureGuid = guid
        }
    }

    /**
     * Assigns a normal map to a material and updates the corresponding GUID in RenderComponent.
     */
    fun setNormalMapWithGuid(
        renderComponent: RenderComponent,
        material: Material,
        texture: Texture
    ) {
        material.normalMap = texture
        resolveTextureGuid(texture, renderComponent) { rc, guid ->
            rc.normalMapGuid = guid
        }
    }

    /**
     * Assigns a metallic-roughness texture to a material and updates the corresponding GUID in RenderComponent.
     */
    fun setMetallicRoughnessWithGuid(
        renderComponent: RenderComponent,
        material: Material,
        texture: Texture
    ) {
        material.metallicRoughnessTexture = texture
        resolveTextureGuid(texture, renderComponent) { rc, guid ->
            rc.metallicRoughnessGuid = guid
        }
    }

    /**
     * Resolves texture GUID from AssetDatabase and updates RenderComponent.
     * Falls back to filePath if GUID lookup fails.
     */
    private fun resolveTextureGuid(
        texture: Texture,
        renderComponent: RenderComponent,
        setGuid: (RenderComponent, String) -> Unit
    ) {
        texture.filePath?.let { texPath ->
            val texFile = File(texPath)
            val absolutePath = if (texFile.isAbsolute) {
                texFile.absolutePath
            } else {
                File(texPath).absolutePath
            }

            val asset = assetDatabase.getByAbsolutePath(absolutePath)
            val guid = asset?.guid?.value ?: absolutePath

            setGuid(renderComponent, guid)

            if (asset != null) {
                logger.log("Resolved texture GUID: ${asset.guid.value.take(8)}", LogLevel.INFO)
            } else {
                logger.log(
                    "WARNING: Texture not found in AssetDatabase, using absolute path: $absolutePath",
                    LogLevel.WARN
                )
            }
        } ?: run {
            logger.log("WARNING: Texture has no filePath, cannot resolve GUID", LogLevel.WARN)
        }
    }
}
