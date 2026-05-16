package com.pafoid.skate.engine.assets.database

import java.io.File

/**
 * Service interface for the asset database.
 *
 * The AssetDatabase manages the relationship between source asset files,
 * their GUIDs (stored in .meta sidecar files), import settings, dependencies,
 * and the asset registry cache.
 */
interface AssetDatabase {
    // ─── Initialization ───────────────────────────────

    /**
     * Initialize the database for the given project root.
     * Loads the registry from disk if it exists.
     */
    fun initialize(projectRoot: File): Result<Unit>

    /**
     * Shut down and persist any pending changes.
     */
    fun shutdown()

    // ─── Scanning & Importing ─────────────────────────

    /**
     * Scan the entire project directory for assets.
     * Creates .meta files for any asset that doesn't have one.
     */
    fun scanAll(): Result<Unit>

    /**
     * Scan a specific path within the project.
     */
    fun scanPath(relativePath: String): Result<Unit>

    /**
     * Import a specific asset by GUID.
     */
    fun importAsset(guid: AssetGuid): Result<Unit>

    /**
     * Import all assets that are dirty (source modified since last import).
     */
    fun importAllDirty(): Result<Unit>

    // ─── Lookup ───────────────────────────────────────

    /**
     * Look up an asset by its GUID.
     */
    fun getByGuid(guid: AssetGuid): AssetInfo?

    /**
     * Look up an asset by its relative source path.
     */
    fun getBySourcePath(relativePath: String): AssetInfo?

    /**
     * Look up an asset by its absolute source path.
     */
    fun getByAbsolutePath(absolutePath: String): AssetInfo?

    /**
     * Get all assets of a given type.
     */
    fun getAllByType(type: AssetType): List<AssetInfo>

    /**
     * Get all known assets.
     */
    fun getAll(): List<AssetInfo>

    // ─── GUID Resolution ──────────────────────────────

    /**
     * Resolve a GUID to a relative source path.
     */
    fun resolveSourcePath(guid: AssetGuid): String?

    /**
     * Resolve a GUID to an absolute source path.
     */
    fun resolveAbsolutePath(guid: AssetGuid): String?

    // ─── Dependency Tracking ──────────────────────────

    /**
     * Get the GUIDs of assets that this asset depends on.
     */
    fun getDependencies(guid: AssetGuid): Set<AssetGuid>

    /**
     * Get the GUIDs of assets that depend on this asset.
     */
    fun getDependents(guid: AssetGuid): Set<AssetGuid>

    /**
     * Get assets whose source files have been modified since last import.
     */
    fun getDirtyAssets(): Set<AssetGuid>

    // ─── Meta File Management ─────────────────────────

    /**
     * Create a new .meta file for the given source file.
     * Returns the generated GUID.
     */
    fun createMeta(sourceFile: File): Result<AssetGuid>

    /**
     * Delete the .meta file for the given GUID.
     */
    fun deleteMeta(guid: AssetGuid): Result<Unit>

    /**
     * Update the import settings for an asset.
     */
    fun updateSettings(guid: AssetGuid, settings: ImporterSettings): Result<Unit>

    // ─── File Move/Rename ─────────────────────────────

    /**
     * Handle a file move or rename operation.
     * Updates the .meta file and registry entries while preserving the GUID.
     */
    fun onFileMoved(oldPath: String, newPath: String): Result<Unit>

    // ─── Persistence ──────────────────────────────────

    /**
     * Export the current registry data for embedding in the project file.
     */
    fun exportRegistryData(): AssetRegistryData

    /**
     * Load registry data from a project file snapshot.
     */
    fun importRegistryData(data: AssetRegistryData)

    /**
     * Save the registry to disk (cache only, for rebuild safety).
     */
    fun saveRegistry(): Result<Unit>

    /**
     * Load the registry from disk cache.
     */
    fun loadRegistry(): Result<Unit>

    // ─── Accessors ────────────────────────────────────

    val projectRoot: File?
    val isInitialized: Boolean
}
