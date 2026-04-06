package com.pafoid.skate.engine.assets.database

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.LogLevel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Implementation of the AssetDatabase service.
 *
 * Manages .meta sidecar files, the in-memory asset index, and the disk registry cache.
 */
class AssetDatabaseImpl(
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val importPipeline: ImportPipeline
) : AssetDatabase {

    private var _projectRoot: File? = null
    override val projectRoot: File? get() = _projectRoot
    override val isInitialized: Boolean get() = _projectRoot != null

    // In-memory indexes
    private val byGuid = mutableMapOf<AssetGuid, AssetInfo>()
    private val bySourcePath = mutableMapOf<String, AssetInfo>()
    private val byAbsolutePath = mutableMapOf<String, AssetInfo>()
    private val reverseDeps = mutableMapOf<AssetGuid, MutableSet<AssetGuid>>()

    // Cache path for registry (derived artifact, not project data)
    private val cacheDir: File?
        get() = _projectRoot?.let { File(it, ".cache") }
    private val registryPath: File?
        get() = cacheDir?.let { File(it, "asset_registry.json") }

    // Directories to skip during scan
    private val skipDirs = setOf(
        ".git", ".idea", "build", "gradle", ".kotlin", ".gradle",
        "out", ".vscode", ".settings", "target", ".cache", "Builds"
    )

    // ─── Initialization ───────────────────────────────

    override fun initialize(projectRoot: File): Result<Unit> {
        return try {
            _projectRoot = projectRoot
            clearIndexes()
            loadRegistry()
            logger.logEngine("AssetDatabase initialized for ${projectRoot.absolutePath}", LogLevel.INFO)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.logEngine("Failed to initialize AssetDatabase: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    override fun shutdown() {
        saveRegistry()
        _projectRoot = null
    }

    // ─── Scanning ─────────────────────────────────────

    override fun scanAll(): Result<Unit> {
        val root = _projectRoot ?: return Result.failure(IllegalStateException("Not initialized"))
        return try {
            walkDirectory(root, root)
            saveRegistry()
            logger.logEngine("Asset scan complete. ${byGuid.size} assets indexed.", LogLevel.INFO)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.logEngine("Asset scan failed: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    override fun scanPath(relativePath: String): Result<Unit> {
        val root = _projectRoot ?: return Result.failure(IllegalStateException("Not initialized"))
        val dir = File(root, relativePath)
        if (!dir.exists()) return Result.failure(FileNotFoundException("Path not found: $relativePath"))
        return try {
            if (dir.isDirectory) {
                walkDirectory(dir, root)
            } else {
                processFile(dir, root)
            }
            saveRegistry()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun walkDirectory(dir: File, root: File) {
        val files = dir.listFiles() ?: return
        for (file in files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))) {
            if (file.isDirectory) {
                if (file.name !in skipDirs) {
                    walkDirectory(file, root)
                }
            } else {
                processFile(file, root)
            }
        }
    }

    private fun processFile(file: File, root: File) {
        // Skip .meta files themselves, the registry file, and the project file
        if (file.extension == "meta" || file.name == ".asset_registry.json" || file.extension == "skateproject") return

        val metaFile = File("${file.absolutePath}.meta")
        if (metaFile.exists()) {
            // .meta exists — verify and update if needed
            syncExistingMeta(metaFile, file, root)
        } else {
            // No .meta — create one
            createMeta(file).onFailure { e ->
                logger.logEngine("Failed to create meta for ${file.name}: ${e.message}", LogLevel.WARN)
            }
        }
    }

    private fun syncExistingMeta(metaFile: File, sourceFile: File, root: File) {
        try {
            val meta = serializer.decode<AssetMeta>(metaFile.readText())
            val guid = AssetGuid(meta.guid)

            // Check if already indexed
            if (byGuid.containsKey(guid)) return

            val relativePath = sourceFile.absolutePath.removePrefix(root.absolutePath + File.separator)
            val info = AssetInfo(
                guid = guid,
                assetType = AssetType.fromExtension(sourceFile.extension),
                importerType = meta.importerType,
                sourcePath = relativePath,
                absoluteSourcePath = sourceFile.absolutePath,
                importTimestamp = meta.importTimestamp,
                lastModified = sourceFile.lastModified(),
                settings = meta.settings,
                dependencies = meta.dependencies.map { AssetGuid(it) }.toSet(),
                generatedOutputPaths = meta.generatedOutputPaths,
                isImported = meta.generatedOutputPaths.isNotEmpty()
            )

            indexAsset(info)
        } catch (e: Exception) {
            logger.logEngine("Failed to sync meta ${metaFile.name}: ${e.message}", LogLevel.WARN)
        }
    }

    // ─── Lookup ───────────────────────────────────────

    override fun getByGuid(guid: AssetGuid): AssetInfo? = byGuid[guid]

    override fun getBySourcePath(relativePath: String): AssetInfo? = bySourcePath[relativePath]

    override fun getByAbsolutePath(absolutePath: String): AssetInfo? = byAbsolutePath[absolutePath]

    override fun getAllByType(type: AssetType): List<AssetInfo> =
        byGuid.values.filter { it.assetType == type }

    override fun getAll(): List<AssetInfo> = byGuid.values.toList()

    override fun resolveSourcePath(guid: AssetGuid): String? = byGuid[guid]?.sourcePath

    override fun resolveAbsolutePath(guid: AssetGuid): String? = byGuid[guid]?.absoluteSourcePath

    // ─── Dependency Tracking ──────────────────────────

    override fun getDependencies(guid: AssetGuid): Set<AssetGuid> =
        byGuid[guid]?.dependencies ?: emptySet()

    override fun getDependents(guid: AssetGuid): Set<AssetGuid> =
        reverseDeps[guid]?.toSet() ?: emptySet()

    override fun getDirtyAssets(): Set<AssetGuid> {
        val root = _projectRoot ?: return emptySet()
        return byGuid.values.filter { info ->
            val file = File(root, info.sourcePath)
            file.exists() && file.lastModified() > info.lastModified
        }.map { it.guid }.toSet()
    }

    // ─── Meta File Management ─────────────────────────

    override fun createMeta(sourceFile: File): Result<AssetGuid> {
        val root = _projectRoot ?: return Result.failure(IllegalStateException("Not initialized"))
        return try {
            val guid = AssetGuid.generate()
            val relativePath = sourceFile.absolutePath.removePrefix(root.absolutePath + File.separator)
            val assetType = AssetType.fromExtension(sourceFile.extension)

            // Determine importer type
            val importer = importPipeline.getImporterForExtension(sourceFile.extension)
            val importerType = importer?.javaClass?.simpleName ?: "UnknownImporter"

            val meta = AssetMeta(
                guid = guid.value,
                assetType = assetType.name,
                importerType = importerType,
                sourcePath = relativePath,
                lastModified = sourceFile.lastModified()
            )

            val metaFile = File("${sourceFile.absolutePath}.meta")
            metaFile.writeText(serializer.encode(meta))

            val info = AssetInfo(
                guid = guid,
                assetType = assetType,
                importerType = importerType,
                sourcePath = relativePath,
                absoluteSourcePath = sourceFile.absolutePath,
                importTimestamp = meta.importTimestamp,
                lastModified = sourceFile.lastModified(),
                settings = meta.settings,
                dependencies = emptySet(),
                generatedOutputPaths = emptyList(),
                isImported = false
            )

            indexAsset(info)
            logger.logEngine("Created .meta for ${sourceFile.name} (GUID: ${guid.value.take(8)}...)", LogLevel.INFO)
            Result.success(guid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteMeta(guid: AssetGuid): Result<Unit> {
        val info = byGuid[guid] ?: return Result.failure(IllegalArgumentException("Asset not found: $guid"))
        return try {
            val root = _projectRoot ?: return Result.failure(IllegalStateException("Not initialized"))
            val metaFile = File(root, "${info.sourcePath}.meta")
            if (metaFile.exists()) metaFile.delete()
            unindexAsset(info)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateSettings(guid: AssetGuid, settings: ImporterSettings): Result<Unit> {
        val info = byGuid[guid] ?: return Result.failure(IllegalArgumentException("Asset not found: $guid"))
        return try {
            val root = _projectRoot ?: return Result.failure(IllegalStateException("Not initialized"))
            val metaFile = File(root, "${info.sourcePath}.meta")

            val meta = if (metaFile.exists()) {
                serializer.decode<AssetMeta>(metaFile.readText())
            } else {
                AssetMeta(
                    guid = guid.value,
                    assetType = info.assetType.name,
                    importerType = info.importerType,
                    sourcePath = info.sourcePath
                )
            }.copy(settings = settings)

            metaFile.writeText(serializer.encode(meta))

            val updatedInfo = info.copy(settings = settings)
            updateAssetInIndexes(updatedInfo)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── File Move/Rename ─────────────────────────────

    override fun onFileMoved(oldPath: String, newPath: String): Result<Unit> {
        val root = _projectRoot ?: return Result.failure(IllegalStateException("Not initialized"))
        val oldFile = File(root, oldPath)
        val newFile = File(root, newPath)
        val info = bySourcePath[oldPath] ?: return Result.failure(IllegalArgumentException("Asset not found at: $oldPath"))

        return try {
            // Move the .meta file
            val oldMetaFile = File("${oldFile.absolutePath}.meta")
            val newMetaFile = File("${newFile.absolutePath}.meta")
            if (oldMetaFile.exists()) {
                oldMetaFile.renameTo(newMetaFile)
            }

            // Update the .meta content
            if (newMetaFile.exists()) {
                val meta = serializer.decode<AssetMeta>(newMetaFile.readText())
                    .copy(sourcePath = newPath)
                newMetaFile.writeText(serializer.encode(meta))
            }

            // Update in-memory indexes
            val updatedInfo = info.copy(
                sourcePath = newPath,
                absoluteSourcePath = newFile.absolutePath
            )
            unindexAsset(info)
            indexAsset(updatedInfo)

            logger.logEngine("Moved asset: $oldPath → $newPath (GUID preserved: ${info.guid.value.take(8)}...)", LogLevel.INFO)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun importAsset(guid: AssetGuid): Result<Unit> {
        val root = _projectRoot ?: return Result.failure(IllegalStateException("Not initialized"))
        val info = byGuid[guid] ?: return Result.failure(IllegalArgumentException("Asset not found: $guid"))
        val sourceFile = File(root, info.sourcePath)

        return try {
            val metaFile = File("${sourceFile.absolutePath}.meta")
            val meta = if (metaFile.exists()) {
                serializer.decode<AssetMeta>(metaFile.readText())
            } else {
                return Result.failure(IllegalStateException("No .meta file for ${info.sourcePath}"))
            }

            val result = runBlocking {
                importPipeline.import(sourceFile, meta, root)
            }

            result.fold(
                onSuccess = { outputPaths ->
                    // Update meta with output paths
                    val updatedMeta = meta.copy(
                        generatedOutputPaths = outputPaths,
                        importTimestamp = System.currentTimeMillis(),
                        lastModified = sourceFile.lastModified()
                    )
                    metaFile.writeText(serializer.encode(updatedMeta))

                    // Update in-memory info
                    val updatedInfo = info.copy(
                        generatedOutputPaths = outputPaths,
                        importTimestamp = updatedMeta.importTimestamp,
                        lastModified = updatedMeta.lastModified,
                        isImported = true
                    )
                    updateAssetInIndexes(updatedInfo)
                },
                onFailure = { e ->
                    // Already logged by ImportPipeline
                }
            )

            if (result.isSuccess) Result.success(Unit) else Result.failure(result.exceptionOrNull() ?: IllegalStateException("Import failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun importAllDirty(): Result<Unit> {
        val dirty = getDirtyAssets()
        var successCount = 0
        var failCount = 0
        for (guid in dirty) {
            importAsset(guid).fold(
                onSuccess = { successCount++ },
                onFailure = { failCount++ }
            )
        }
        saveRegistry()
        logger.logEngine("Import complete: $successCount succeeded, $failCount failed", LogLevel.INFO)
        return Result.success(Unit)
    }

    // ─── Persistence ──────────────────────────────────

    override fun saveRegistry(): Result<Unit> {
        val registryFile = registryPath ?: return Result.failure(IllegalStateException("Not initialized"))
        return try {
            // Ensure cache directory exists
            registryFile.parentFile?.mkdirs()

            val data = exportRegistryData()
            registryFile.writeText(serializer.encode(data))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.logEngine("Failed to save registry: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    override fun loadRegistry(): Result<Unit> {
        val registryFile = registryPath ?: return Result.failure(IllegalStateException("Not initialized"))
        return try {
            // Migrate: if old root-level registry exists, move it to cache
            migrateOldRegistryIfPresent()

            if (registryFile.exists()) {
                val data = serializer.decode<AssetRegistryData>(registryFile.readText())
                loadRegistryFromData(data)
                logger.logEngine("Loaded registry with ${byGuid.size} assets", LogLevel.INFO)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.logEngine("Failed to load registry: ${e.message}", LogLevel.WARN)
            Result.success(Unit) // Start fresh on error
        }
    }

    override fun exportRegistryData(): AssetRegistryData {
        return AssetRegistryData(
            projectPath = _projectRoot?.absolutePath ?: "",
            lastScanTimestamp = System.currentTimeMillis(),
            assets = byGuid.values.associateBy(
                { it.guid.value },
                { info ->
                    RegistryAssetEntry(
                        guid = info.guid.value,
                        assetType = info.assetType.name,
                        sourcePath = info.sourcePath,
                        importerType = info.importerType,
                        importTimestamp = info.importTimestamp,
                        lastModified = info.lastModified,
                        dependencies = info.dependencies.map { it.value },
                        generatedOutputPaths = info.generatedOutputPaths,
                        isImported = info.isImported
                    )
                }
            )
        )
    }

    override fun importRegistryData(data: AssetRegistryData) {
        clearIndexes()
        val root = File(data.projectPath)
        for ((guidStr, entry) in data.assets) {
            val info = AssetInfo(
                guid = AssetGuid(guidStr),
                assetType = AssetType.valueOf(entry.assetType),
                importerType = entry.importerType,
                sourcePath = entry.sourcePath,
                absoluteSourcePath = File(root, entry.sourcePath).absolutePath,
                importTimestamp = entry.importTimestamp,
                lastModified = entry.lastModified,
                settings = ImporterSettings(),
                dependencies = entry.dependencies.map { AssetGuid(it) }.toSet(),
                generatedOutputPaths = entry.generatedOutputPaths,
                isImported = entry.isImported
            )
            indexAsset(info)
        }
        logger.logEngine("Imported registry with ${byGuid.size} assets from project file", LogLevel.INFO)
    }

    /**
     * Migrate old root-level .asset_registry.json to .cache/ directory.
     */
    private fun migrateOldRegistryIfPresent() {
        val root = _projectRoot ?: return
        val oldFile = File(root, ".asset_registry.json")
        if (oldFile.exists()) {
            try {
                cacheDir?.mkdirs()
                oldFile.copyTo(registryPath!!, overwrite = true)
                oldFile.delete()
                logger.logEngine("Migrated asset registry to .cache/ directory", LogLevel.INFO)
            } catch (e: Exception) {
                logger.logEngine("Failed to migrate old registry: ${e.message}", LogLevel.WARN)
            }
        }
    }

    /**
     * Load registry data into the in-memory indexes.
     */
    private fun loadRegistryFromData(data: AssetRegistryData) {
        clearIndexes()
        val root = File(data.projectPath)
        for ((guidStr, entry) in data.assets) {
            val info = AssetInfo(
                guid = AssetGuid(guidStr),
                assetType = AssetType.valueOf(entry.assetType),
                importerType = entry.importerType,
                sourcePath = entry.sourcePath,
                absoluteSourcePath = File(root, entry.sourcePath).absolutePath,
                importTimestamp = entry.importTimestamp,
                lastModified = entry.lastModified,
                settings = ImporterSettings(),
                dependencies = entry.dependencies.map { AssetGuid(it) }.toSet(),
                generatedOutputPaths = entry.generatedOutputPaths,
                isImported = entry.isImported
            )
            indexAsset(info)
        }
    }

    // ─── Internal Helpers ─────────────────────────────

    private fun clearIndexes() {
        byGuid.clear()
        bySourcePath.clear()
        byAbsolutePath.clear()
        reverseDeps.clear()
    }

    private fun indexAsset(info: AssetInfo) {
        byGuid[info.guid] = info
        bySourcePath[info.sourcePath] = info
        byAbsolutePath[info.absoluteSourcePath] = info

        // Build reverse dependency index
        for (depGuid in info.dependencies) {
            reverseDeps.getOrPut(depGuid) { mutableSetOf() }.add(info.guid)
        }
    }

    private fun unindexAsset(info: AssetInfo) {
        byGuid.remove(info.guid)
        bySourcePath.remove(info.sourcePath)
        byAbsolutePath.remove(info.absoluteSourcePath)

        // Remove from reverse deps
        for (depGuid in info.dependencies) {
            reverseDeps[depGuid]?.remove(info.guid)
            if (reverseDeps[depGuid]?.isEmpty() == true) {
                reverseDeps.remove(depGuid)
            }
        }
    }

    private fun updateAssetInIndexes(updatedInfo: AssetInfo) {
        val oldInfo = byGuid[updatedInfo.guid]
        if (oldInfo != null) {
            unindexAsset(oldInfo)
        }
        indexAsset(updatedInfo)
    }
}
