package com.pafoid.skate.engine.assets.database

/**
 * In-memory resolved asset record.
 *
 * This is the runtime representation of an asset, combining data from the
 * `.meta` sidecar file with resolved absolute paths and dependency references.
 */
data class AssetInfo(
    val guid: AssetGuid,
    val assetType: AssetType,
    val importerType: String,
    val sourcePath: String,
    val absoluteSourcePath: String,
    val importTimestamp: Long,
    val lastModified: Long,
    val settings: ImporterSettings,
    val dependencies: Set<AssetGuid>,
    val generatedOutputPaths: List<String>,
    val isImported: Boolean
)
