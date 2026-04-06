package com.pafoid.skate.engine.assets.database

import kotlinx.serialization.Serializable

/**
 * Disk cache format for the asset registry.
 *
 * Stored at `{ProjectRoot}/.asset_registry.json`. This is a derived cache that
 * can be rebuilt at any time by scanning `.meta` files. It maps GUIDs to
 * flattened registry entries for efficient lookup.
 */
@Serializable
data class AssetRegistryData(
    val version: Int = 1,
    val projectPath: String,
    val lastScanTimestamp: Long = 0L,
    val assets: Map<String, RegistryAssetEntry> = emptyMap()
)

/**
 * A flattened entry in the asset registry disk cache.
 */
@Serializable
data class RegistryAssetEntry(
    val guid: String,
    val assetType: String,
    val sourcePath: String,
    val importerType: String,
    val importTimestamp: Long,
    val lastModified: Long,
    val dependencies: List<String> = emptyList(),
    val generatedOutputPaths: List<String> = emptyList(),
    val isImported: Boolean = false
)
