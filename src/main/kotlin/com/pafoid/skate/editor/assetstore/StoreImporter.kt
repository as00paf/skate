package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.render.material.MaterialAsset
import java.io.File
import java.io.IOException
import java.time.Instant

/** Everything needed to import one staged store item (built on an IO worker, executed on the main thread). */
data class StoreImportRequest(
    val details: StoreAssetDetails,
    val variant: StoreVariant,
    val stagingDir: File,
    val plan: ImportPlan,
    val modifications: List<String> = emptyList(),
) {
    val title: String get() = details.summary.title
}

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
 * Copies staged files into `Assets/Store/<provider>/<slug>/` (AST-16 §7): one `registry.register(file)` per file, the
 * `store.*` sidecar settings, an optional `.skmat`, and the ledger entry. A re-import of the same item overwrites in
 * place and leaves sidecars alone, so GUIDs survive; [revert] goes through `AssetRegistry.delete`.
 */
class StoreImporter(
    private val projectDir: File,
    private val registry: AssetRegistry,
    private val clock: () -> Instant = Instant::now,
) {
    fun import(request: StoreImportRequest): ImportOutcome {
        val details = request.details
        val ref = details.summary.ref
        if (!details.license.isKnown) throw IOException("Unknown license for ${ref.sourceId}; import blocked")

        val ledger = AttributionLedger.load(projectDir)
        val previous = ledger.find(ref.providerId, ref.sourceId)
        val targetDir = previous?.folder?.takeIf { it.isNotBlank() }?.let { File(projectDir, it) } ?: freshTargetDir(ref)
        val backupDir = File(request.stagingDir.parentFile, "backup-${clock().toEpochMilli()}")

        val sources = request.plan.entries.map { entry ->
            val target = StorePaths.safeRelative(entry.targetPath)?.takeIf { StoreFormats.isImportable(it) }
                ?: throw IOException("Not importable: ${entry.targetPath}")
            val staged = StorePaths.resolveInside(request.stagingDir, entry.stagedPath)?.takeIf { it.isFile }
                ?: throw IOException("Missing staged file: ${entry.stagedPath}")
            staged to target
        }

        val oldFiles = if (previous != null && targetDir.isDirectory) projectFilesIn(targetDir) else emptyList()
        val created = ArrayList<File>()
        val backups = LinkedHashMap<File, File>()
        val removed = ArrayList<File>()
        val files = ArrayList<File>()
        val progress = ImportOutcome(targetDir, files, created, backups, removed, previous)
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
                backups[old] = backup(old, backupDir, withSidecar = true)
                registry.delete(old)
                removed += old
            }
            val ids = files.map { file -> tag(file, ref, details.license) }
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
                folder = targetDir.relativeTo(projectDir).invariantSeparatorsPath,
            )
            AttributionLedger.save(projectDir, ledger.upsert(entry))
        } catch (e: Exception) {
            revert(progress, ref)
            throw e
        }
        return progress
    }

    /** Undoes [outcome]: created files via `AssetRegistry.delete`, overwritten/removed files restored from backups. */
    fun revert(outcome: ImportOutcome, ref: StoreAssetRef) {
        outcome.created.forEach { registry.delete(it) }
        for ((file, backup) in outcome.backups) {
            if (!backup.isFile) continue
            file.parentFile?.mkdirs()
            backup.copyTo(file, overwrite = true)
            val sidecarBackup = AssetMeta.sidecarOf(backup)
            if (sidecarBackup.isFile) sidecarBackup.copyTo(AssetMeta.sidecarOf(file), overwrite = true)
            registry.register(file)
        }
        deleteEmptyDirs(outcome.targetDir)
        val ledger = AttributionLedger.load(projectDir)
        val reverted = outcome.previousEntry?.let { ledger.upsert(it) } ?: ledger.remove(ref.providerId, ref.sourceId)
        AttributionLedger.save(projectDir, reverted)
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
            if (dst !in backups) backups[dst] = backup(dst, backupDir, withSidecar = false)
        } else {
            created += dst
        }
        dst.parentFile.mkdirs()
        copy()
    }

    private fun backup(file: File, backupDir: File, withSidecar: Boolean): File {
        val rel = file.relativeTo(projectDir).invariantSeparatorsPath
        val copy = File(backupDir, rel)
        copy.parentFile.mkdirs()
        file.copyTo(copy, overwrite = true)
        if (withSidecar) AssetMeta.sidecarOf(file).takeIf { it.isFile }?.copyTo(AssetMeta.sidecarOf(copy), overwrite = true)
        return copy
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
    }
}
