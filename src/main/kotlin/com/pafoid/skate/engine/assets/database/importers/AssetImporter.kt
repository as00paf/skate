package com.pafoid.skate.engine.assets.database.importers

import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.database.AssetMeta
import com.pafoid.skate.engine.assets.database.AssetType
import java.io.File

/**
 * Interface for asset importers.
 *
 * Each importer handles a specific asset type (texture, model, audio, shader).
 * Importers convert source files into runtime-ready formats and can load
 * already-imported assets into the engine.
 */
interface AssetImporter {
    val assetType: AssetType
    val supportedExtensions: Set<String>

    /**
     * Import a source file into runtime-ready format.
     *
     * @param sourceFile The source asset file on disk
     * @param meta The asset metadata (GUID, settings, etc.)
     * @param projectRoot The project root directory
     * @return List of generated output paths (relative to project root)
     */
    suspend fun import(
        sourceFile: File,
        meta: AssetMeta,
        projectRoot: File
    ): Result<List<String>>

    /**
     * Load an already-imported asset into the engine's runtime.
     *
     * Called by ResourceManager when the engine needs the asset.
     *
     * @param guid The asset's GUID
     * @param meta The asset metadata
     * @param projectRoot The project root directory
     * @return The loaded runtime object (e.g. texture path, model path)
     */
    fun loadRuntime(guid: AssetGuid, meta: AssetMeta, projectRoot: File): Result<Any>
}
