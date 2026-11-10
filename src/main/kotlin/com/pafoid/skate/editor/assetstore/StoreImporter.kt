package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.render.material.MaterialAsset
import java.io.File
import java.io.IOException
import java.time.Instant

/** Everything needed to import one staged store item (built on an IO worker, executed by a command). */
data class StoreImportRequest(
    val details: StoreAssetDetails,
    val variant: StoreVariant,
    val stagingDir: File,
    val plan: ImportPlan,
    val modifications: List<String> = emptyList(),
) {
    val title: String get() = details.summary.title
    val ref: StoreAssetRef get() = details.summary.ref
}

/**
 * Result of [StoreImporter.stage]: files are copied and backed up, nothing is registered yet.
 * [backups] maps a project file to its previous content; the sidecar backup sits next to it (`AssetMeta.sidecarOf`).
 */
class StagedImport internal constructor(
    val request: StoreImportRequest,
    val targetDir: File,
    val files: List<File>,
    val created: List<File>,
    val backups: Map<File, File>,
    /** Files of the previous version that the new version no longer has (backed up, still on disk). */
    val toRemove: List<File>,
    val previousEntry: LedgerEntry?,
    val backupDir: File,
)

/** What an import changed, so [StoreImporter.revert] can undo it exactly. */
class ImportOutcome(
    val targetDir: File,
    /** Every file of the imported version (registered, with `store.*` sidecar settings). */
    val files: List<File>,
    val created: List<File>,
    /** Previous content of files the import overwrote or removed; the key is the project file. */
    val backups: Map<File, File>,
    /** Files of the previous version that the new version no longer has. */
    val removed: List<File>,
    val previousEntry: LedgerEntry?,
)

/**
 * Copies staged files into `Assets/Store/<provider>/<slug>/` (AST-16 §7) in two phases:
 * - [stage] (IO worker): checks, target folder, backups (files and sidecars), copies, `.skmat`. No registry or ledger.
 * - [commit] (main thread): removes dropped files via `AssetRegistry.delete`, one `registry.register(file)` per file,
 *   `store.*` sidecar settings, ledger entry.
 *
 * A re-import of the same item overwrites in place and keeps sidecars, so GUIDs survive; [revert] goes through
 * `AssetRegistry.delete` and restores backed-up files and sidecars.
 */
class StoreImporter(
    private val projectDir: File,
    private val registry: AssetRegistry,
    private val clock: () -> Instant = Instant::now,
) {
    /** Both phases on the calling thread. */
    fun import(request: StoreImportRequest): ImportOutcome = commit(stage(request))

    fun stage(request: StoreImportRequest): StagedImport {
        val details = request.details
        val ref = request.ref
        if (!details.license.isKnown) throw IOException("Unknown license for ${ref.sourceId}; import blocked")
        requireStagingInProject(request.stagingDir)

        val previous = AttributionLedger.load(projectDir).find(ref.providerId, ref.sourceId)
        val previousDir = previous?.folder?.let(::ledgerFolder)
        val targetDir = previousDir ?: freshTargetDir(ref)
        val backupDir = File(request.stagingDir.parentFile, "$BACKUP_PREFIX${clock().toEpochMilli()}")

        val sources = request.plan.entries.map { entry ->
            val target = StorePaths.safeRelative(entry.targetPath)?.takeIf { StoreFormats.isImportable(it) }
                ?: throw IOException("Not importable: ${entry.targetPath}")
            val staged = StorePaths.resolveInside(request.stagingDir, entry.stagedPath)?.takeIf { it.isFile }
                ?: throw IOException("Missing staged file: ${entry.stagedPath}")
            staged to target
        }

        val oldFiles = if (previousDir != null && targetDir.isDirectory) projectFilesIn(targetDir) else emptyList()
        val created = ArrayList<File>()
        val backups = LinkedHashMap<File, File>()
        val files = ArrayList<File>()
        val toRemove = ArrayList<File>()
        try {
            for ((staged, target) in sources) {
                val dst = StorePaths.resolveInside(targetDir, target) ?: throw IOException("Path escapes import folder: $target")
                write(dst, created, backups, backupDir) { staged.copyTo(dst, overwrite = true) }
                files += dst
            }
            request.plan.material?.let { spec ->
                val dst = File(targetDir, StorePaths.slug(spec.name) + "." + MaterialAsset.EXTENSION)
                val textures = spec.textures.mapValues { (_, path) -> AssetPaths.relativize(File(targetDir, path).absolutePath, projectDir) }
                write(dst, created, backups, backupDir) { MaterialAsset(name = spec.name, textures = textures).save(dst) }
                files += dst
            }
            for (old in oldFiles) {
                if (files.any { it.absoluteFile == old.absoluteFile }) continue
                backups[old] = backup(old, backupDir)
                toRemove += old
            }
        } catch (e: Exception) {
            rollbackStage(created, backups)
            backupDir.deleteRecursively()
            throw e
        }
        return StagedImport(request, targetDir, files, created, backups, toRemove, previous, backupDir)
    }

    fun commit(staged: StagedImport): ImportOutcome {
        val request = staged.request
        val ref = request.ref
        val details = request.details
        val removed = ArrayList<File>()
        val outcome = ImportOutcome(staged.targetDir, staged.files, staged.created, staged.backups, removed, staged.previousEntry)
        try {
            for (old in staged.toRemove) {
                registry.delete(old)
                removed += old
            }
            val ids = staged.files.map { file -> tag(file, ref, details.license) }
            val entry = LedgerEntry(
                assetIds = ids,
                provider = ref.providerId,
                sourceId = ref.sourceId,
                revision = ref.revision,
                variant = request.variant.id,
                title = details.summary.title,
                sourceUrl = details.sourceUrl,
                authors = details.authors.map { LedgerAuthor(it.name, it.url) },
                license = LedgerLicense(details.license.spdx, details.license.url),
                modifications = request.modifications + ndWarning(details.license, request.modifications),
                retrievedAt = clock().toString(),
                folder = staged.targetDir.relativeTo(projectDir).invariantSeparatorsPath,
            )
            AttributionLedger.save(projectDir, AttributionLedger.load(projectDir).upsert(entry))
        } catch (e: Exception) {
            revert(outcome, ref)
            throw e
        }
        pruneBackups(request.stagingDir.parentFile)
        return outcome
    }

    /** Undoes a staged-but-not-committed import (IO failure or a rejected request): raw file operations only. */
    fun discard(staged: StagedImport) {
        rollbackStage(staged.created, staged.backups)
        deleteEmptyDirs(staged.targetDir)
    }

    /** Undoes [outcome]: created files via `AssetRegistry.delete`, overwritten/removed files restored from backups. */
    fun revert(outcome: ImportOutcome, ref: StoreAssetRef) {
        outcome.created.forEach { registry.delete(it) }
        for ((file, backup) in outcome.backups) {
            if (!backup.isFile) continue
            restore(file, backup)
            registry.register(file)
        }
        deleteEmptyDirs(outcome.targetDir)
        val ledger = AttributionLedger.load(projectDir)
        val reverted = outcome.previousEntry?.let { ledger.upsert(it) } ?: ledger.remove(ref.providerId, ref.sourceId)
        AttributionLedger.save(projectDir, reverted)
    }

    /** The ledger's `folder` if it resolves inside `Assets/Store/` of this project; a shared ledger is not trusted. */
    internal fun ledgerFolder(folder: String): File? {
        if (folder.isBlank()) return null
        val dir = StorePaths.resolveInside(projectDir, folder) ?: return null
        val store = StorePaths.storeDir(projectDir).canonicalFile
        return dir.takeIf { it.path.startsWith(store.path + File.separator) }
    }

    private fun requireStagingInProject(stagingDir: File) {
        if (!StorePaths.isProjectStaging(projectDir, stagingDir)) {
            throw IOException("Staging folder is not in the open project: ${stagingDir.path}")
        }
    }

    private fun freshTargetDir(ref: StoreAssetRef): File {
        val slug = StorePaths.slug(ref.sourceId)
        var dir = StorePaths.importDir(projectDir, ref.providerId, slug)
        var n = 2
        while (dir.exists() && (dir.listFiles()?.isNotEmpty() == true)) dir = StorePaths.importDir(projectDir, ref.providerId, "${slug}_${n++}")
        return dir
    }

    private inline fun write(dst: File, created: MutableList<File>, backups: MutableMap<File, File>, backupDir: File, copy: () -> Unit) {
        if (dst.isFile) {
            if (dst !in backups) backups[dst] = backup(dst, backupDir)
        } else {
            created += dst
        }
        dst.parentFile.mkdirs()
        copy()
    }

    /** Copies [file] and its sidecar (when present) under [backupDir], keeping the project-relative layout. */
    private fun backup(file: File, backupDir: File): File {
        val rel = file.relativeTo(projectDir).invariantSeparatorsPath
        val copy = File(backupDir, rel)
        copy.parentFile.mkdirs()
        file.copyTo(copy, overwrite = true)
        AssetMeta.sidecarOf(file).takeIf { it.isFile }?.copyTo(AssetMeta.sidecarOf(copy), overwrite = true)
        return copy
    }

    private fun restore(file: File, backup: File) {
        file.parentFile?.mkdirs()
        backup.copyTo(file, overwrite = true)
        val sidecarBackup = AssetMeta.sidecarOf(backup)
        if (sidecarBackup.isFile) sidecarBackup.copyTo(AssetMeta.sidecarOf(file), overwrite = true)
    }

    private fun rollbackStage(created: List<File>, backups: Map<File, File>) {
        created.forEach { it.delete() }
        backups.forEach { (file, backup) -> if (backup.isFile) restore(file, backup) }
    }

    /** Keeps the newest [KEEP_BACKUPS] backup folders of one item (older undo steps can no longer restore). */
    private fun pruneBackups(itemDir: File?) {
        val dirs = itemDir?.listFiles { f -> f.isDirectory && f.name.startsWith(BACKUP_PREFIX) }.orEmpty()
        dirs.sortedByDescending { it.name.removePrefix(BACKUP_PREFIX).toLongOrNull() ?: 0L }.drop(KEEP_BACKUPS).forEach { it.deleteRecursively() }
    }

    /** Registers [file] (keeping an existing sidecar's GUID) and adds the `store.*` settings; no `AssetMeta` schema bump. */
    private fun tag(file: File, ref: StoreAssetRef, license: LicenseInfo): String {
        val id = registry.register(file)
        val meta = AssetMeta.read(file) ?: AssetMeta(guid = id, importer = AssetMeta.importerFor(file))
        val settings = meta.settings + mapOf(
            SETTING_PROVIDER to ref.providerId,
            SETTING_SOURCE_ID to ref.sourceId,
            SETTING_LICENSE to license.spdx,
        )
        AssetMeta.write(file, meta.copy(settings = settings))
        return id.value
    }

    private fun ndWarning(license: LicenseInfo, modifications: List<String>): List<String> =
        if (!license.derivatives && modifications.isNotEmpty()) listOf(ND_NOTE) else emptyList()

    private fun projectFilesIn(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && !AssetMeta.isSidecar(it) }.toList()

    private fun deleteEmptyDirs(dir: File) {
        if (!dir.isDirectory) return
        dir.walkBottomUp().filter { it.isDirectory && it.listFiles()?.isEmpty() == true }.forEach { it.delete() }
    }

    companion object {
        const val SETTING_PROVIDER = "store.provider"
        const val SETTING_SOURCE_ID = "store.sourceId"
        const val SETTING_LICENSE = "store.license"
        const val ND_NOTE = "no-derivatives license: format conversion recorded"
        const val BACKUP_PREFIX = "backup-"
        const val KEEP_BACKUPS = 5

        /** Deletes every re-import backup of the project (on project close, when the undo history goes away). */
        fun pruneAllBackups(projectDir: File) {
            val root = File(projectDir, "${StorePaths.PROJECT_HIDDEN_DIR}/${StorePaths.STAGING_DIR}")
            if (!root.isDirectory) return
            root.walkTopDown().filter { it.isDirectory && it.name.startsWith(BACKUP_PREFIX) }.toList().forEach { it.deleteRecursively() }
        }
    }
}
