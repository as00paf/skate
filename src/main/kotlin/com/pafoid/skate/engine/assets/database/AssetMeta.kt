package com.pafoid.skate.engine.assets.database

import kotlinx.serialization.Serializable

/**
 * Data model for `.meta` sidecar files.
 *
 * Each source asset file (e.g. `Assets/Models/chair.fbx`) has a corresponding
 * `.meta` file (`Assets/Models/chair.fbx.meta`) storing its GUID, import settings,
 * dependencies, and import metadata.
 */
@Serializable
data class AssetMeta(
    val guid: String,
    val version: Int = 1,
    val assetType: String,
    val importerType: String,
    val sourcePath: String,
    val importTimestamp: Long = System.currentTimeMillis(),
    val lastModified: Long = 0L,
    val settings: ImporterSettings = ImporterSettings(),
    val dependencies: List<String> = emptyList(),
    val generatedOutputPaths: List<String> = emptyList()
)

/**
 * Import settings for all supported asset types.
 * Only the relevant sub-settings will be populated for a given asset type.
 */
@Serializable
data class ImporterSettings(
    val textureSettings: TextureImportSettings? = null,
    val modelImportSettings: ModelImportSettings? = null,
    val audioImportSettings: AudioImportSettings? = null,
    val shaderImportSettings: ShaderImportSettings? = null
)

@Serializable
data class TextureImportSettings(
    val srgb: Boolean = true,
    val generateMipmaps: Boolean = true,
    val maxSize: Int = 4096,
    val compression: String = "none"
)

@Serializable
data class ModelImportSettings(
    val generateColliders: Boolean = false,
    val colliderType: String = "mesh",
    val optimizeMesh: Boolean = true,
    val importAnimations: Boolean = true,
    val unitScale: Float = 1.0f
)

@Serializable
data class AudioImportSettings(
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val loop: Boolean = false,
    val loadStrategy: String = "decompress"
)

@Serializable
data class ShaderImportSettings(
    val defines: List<String> = emptyList(),
    val includePaths: List<String> = emptyList()
)
