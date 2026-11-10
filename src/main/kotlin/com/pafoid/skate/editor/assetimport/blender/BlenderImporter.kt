package com.pafoid.skate.editor.assetimport.blender

import com.pafoid.skate.editor.tools.external.BlenderCommand
import com.pafoid.skate.editor.tools.external.BlenderInstall
import com.pafoid.skate.editor.tools.external.BlenderVersion
import com.pafoid.skate.editor.tools.external.CancelToken
import com.pafoid.skate.editor.tools.external.ProcessRunner
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/** Per-`.blend` import settings, stored in its `.meta` `settings` (AST-19 §4.3); no `AssetMeta` schema change. */
data class BlenderImportSettings(
    val applyModifiers: Boolean = true,
    val onlyVisible: Boolean = false,
    val exportActions: Boolean = true,
    val actionMode: ActionMode = ActionMode.FILES,
    val autoReimport: Boolean = true,
) {
    enum class ActionMode { FILES, EMBEDDED }

    /** The `blender.*` sidecar keys for these settings (all of them, so the sidecar shows what is used). */
    fun toSettings(): Map<String, String> = mapOf(
        APPLY_MODIFIERS to applyModifiers.toString(),
        ONLY_VISIBLE to onlyVisible.toString(),
        EXPORT_ACTIONS to exportActions.toString(),
        ACTION_MODE to actionMode.name,
        AUTO_REIMPORT to autoReimport.toString(),
    )

    /** [meta] with these settings written over its import settings; the import record and other keys are kept. */
    fun applyTo(meta: AssetMeta): AssetMeta = meta.copy(settings = meta.settings + toSettings())

    companion object {
        const val APPLY_MODIFIERS = "blender.applyModifiers"
        const val ONLY_VISIBLE = "blender.onlyVisible"
        const val EXPORT_ACTIONS = "blender.exportActions"
        const val ACTION_MODE = "blender.actionMode"
        const val AUTO_REIMPORT = "blender.autoReimport"

        fun from(settings: Map<String, String>) = BlenderImportSettings(
            applyModifiers = settings[APPLY_MODIFIERS]?.toBooleanStrictOrNull() ?: true,
            onlyVisible = settings[ONLY_VISIBLE]?.toBooleanStrictOrNull() ?: false,
            exportActions = settings[EXPORT_ACTIONS]?.toBooleanStrictOrNull() ?: true,
            actionMode = settings[ACTION_MODE]?.let { v -> ActionMode.entries.firstOrNull { it.name.equals(v, true) } } ?: ActionMode.FILES,
            autoReimport = settings[AUTO_REIMPORT]?.toBooleanStrictOrNull() ?: true,
        )
    }
}

/** The last import of a `.blend`, as recorded in its sidecar by [BlenderImporter.apply] (shown in the inspector). */
data class BlenderImportRecord(val blenderVersion: String, val importedAt: String, val outputs: List<String>) {
    companion object {
        /** Null when the file has never been imported. */
        fun from(meta: AssetMeta?): BlenderImportRecord? {
            val settings = meta?.settings ?: return null
            val at = settings[BlenderImporter.IMPORTED_AT] ?: return null
            return BlenderImportRecord(settings[BlenderImporter.BLENDER_VERSION].orEmpty(), at, BlenderOutputs.recorded(meta))
        }
    }
}

/** `report.json` written by `skate_export.py`. */
@Serializable
data class BlenderReport(
    val blender: String = "",
    val unitScale: Double = 1.0,
    val simplifiedMaterials: List<SimplifiedMaterial> = emptyList(),
    val missingImages: List<MissingImage> = emptyList(),
    val libraries: List<String> = emptyList(),
    val actions: List<ExportedAction> = emptyList(),
    val skippedActions: List<SkippedAction> = emptyList(),
    val outputs: List<String> = emptyList(),
) {
    @Serializable data class SimplifiedMaterial(val material: String, val nodes: List<String> = emptyList())
    @Serializable data class MissingImage(val image: String, val path: String = "")
    @Serializable data class ExportedAction(val action: String, val file: String)
    @Serializable data class SkippedAction(val action: String, val reason: String = "")

    /** Things the user should know about, in plain words (shown in the console). */
    fun warnings(): List<String> = buildList {
        if (unitScale != 1.0) add("Scene unit scale is $unitScale; the model may come in at the wrong size")
        simplifiedMaterials.forEach { add("Material '${it.material}' was simplified on export (unsupported nodes: ${it.nodes.joinToString()})") }
        missingImages.forEach { add("Image '${it.image}' not found (${it.path})") }
        libraries.forEach { add("Linked library $it: linked data is exported as it is in that file") }
        skippedActions.forEach { add("Action '${it.action}' skipped: ${it.reason}") }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(text: String): BlenderReport = json.decodeFromString(serializer(), text)
    }
}

/** Blender's output for one `.blend`, still in the staging folder (IO side of an import). */
sealed interface BlenderConversion {
    val blend: File

    /** The `.blend` has not changed since its last import and every output exists. */
    data class UpToDate(override val blend: File) : BlenderConversion

    data class Converted(override val blend: File, val stagingDir: File, val report: BlenderReport, val sourceHash: String, val blenderVersion: String) : BlenderConversion

    data class Failed(override val blend: File, val reason: String, val log: String = "") : BlenderConversion
}

/** What an applied import changed in the project. */
data class BlenderImportResult(val blend: File, val outputs: List<File>, val keptStale: List<File>, val warnings: List<String>)

/**
 * `.blend` → GLB import (AST-19 §4). [convert] runs Blender into `.skate/cache/blender/` (IO worker, blocking);
 * [apply] copies the results into `<file>.blend.import/` next to the source, in place so existing `.meta` sidecars and
 * GUIDs survive, registers them and records the import in the `.blend`'s sidecar (main thread). A failed conversion
 * leaves the previous outputs untouched. Outputs that a re-import no longer produces (an action deleted in Blender)
 * are kept, with a warning (human decision, AST-19).
 */
class BlenderImporter(
    private val projectDir: File,
    private val registry: AssetRegistry,
    private val runner: ProcessRunner,
    private val blender: () -> BlenderInstall?,
    private val script: () -> File,
    private val timeout: () -> Duration = { Duration.ofMinutes(2) },
    private val clock: () -> Instant = Instant::now,
) {
    /** IO worker. [force] re-exports even when the `.blend` is unchanged. */
    fun convert(blend: File, force: Boolean = false, cancel: CancelToken = CancelToken()): BlenderConversion {
        if (!blend.isFile) return BlenderConversion.Failed(blend, "File not found: ${blend.path}")
        val meta = AssetMeta.read(blend)
        val hash = sha256(blend)
        val previous = BlenderOutputs.recorded(meta)
        if (!force && meta?.settings?.get(SOURCE_HASH) == hash && previous.isNotEmpty() && previous.all { File(BlenderOutputs.dir(blend), it).isFile }) {
            return BlenderConversion.UpToDate(blend)
        }
        val install = blender()
            ?: return BlenderConversion.Failed(blend, "Blender ${BlenderVersion.MINIMUM}+ not found (set its path in Editor Settings)")
        val settings = BlenderImportSettings.from(meta?.settings.orEmpty())
        val staging = stagingDir(blend).apply { deleteRecursively(); mkdirs() }
        val args = buildJsonObject {
            put("out_dir", staging.absolutePath)
            put("name", BlenderOutputs.baseName(blend))
            put("action_mode", settings.actionMode.name)
            put("apply_modifiers", settings.applyModifiers)
            put("only_visible", settings.onlyVisible)
            put("export_actions", settings.exportActions)
        }.toString()
        // A file, not an argument: Windows command lines mangle the quotes inside JSON
        val argsFile = File(staging, ARGS_FILE).apply { writeText(args) }
        val command = BlenderCommand.build(install.executable, blend.absoluteFile, script(), listOf(argsFile.absolutePath))
        val result = runner.run(command, blend.parentFile, timeout(), cancel)
        if (result.cancelled) return BlenderConversion.Failed(blend, "Import cancelled")
        if (result.timedOut) return BlenderConversion.Failed(blend, "Blender timed out after ${timeout().seconds} s", result.tail())
        if (!result.isSuccess) return BlenderConversion.Failed(blend, "Blender exited with code ${result.exitCode}", result.tail())

        val reportFile = File(staging, REPORT_FILE)
        val report = runCatching { BlenderReport.decode(reportFile.readText()) }.getOrElse {
            return BlenderConversion.Failed(blend, "Blender wrote no import report", result.tail())
        }
        val main = BlenderOutputs.mainFileName(blend)
        if (main !in report.outputs || File(staging, main).length() == 0L) {
            return BlenderConversion.Failed(blend, "Blender produced no $main", result.tail())
        }
        report.outputs.firstOrNull { !BlenderOutputs.isSafeName(it) || !File(staging, it).isFile }?.let {
            return BlenderConversion.Failed(blend, "Unexpected output '$it'", result.tail())
        }
        return BlenderConversion.Converted(blend, staging, report, hash, install.version.toString())
    }

    /** Main thread: moves a [BlenderConversion.Converted] result into the project. */
    fun apply(conversion: BlenderConversion.Converted): BlenderImportResult {
        val blend = conversion.blend
        val outDir = BlenderOutputs.dir(blend).apply { mkdirs() }
        val names = conversion.report.outputs + REPORT_FILE
        val outputs = names.map { name ->
            val target = File(outDir, name)
            // Replace atomically so the model hot reload never reads a half-written GLB; the sidecar stays.
            val tmp = File(outDir, "$name.part")
            File(conversion.stagingDir, name).copyTo(tmp, overwrite = true)
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            registry.register(target)
            target
        }
        val previous = BlenderOutputs.recorded(AssetMeta.read(blend))
        val stale = previous.filter { it !in conversion.report.outputs }.map { File(outDir, it) }.filter { it.isFile }

        registry.register(blend)
        val meta = AssetMeta.read(blend) ?: error("Sidecar missing for ${blend.path}")
        AssetMeta.write(blend, meta.copy(
            importer = IMPORTER,
            settings = meta.settings + mapOf(
                OUTPUTS to conversion.report.outputs.joinToString(","),
                SOURCE_HASH to conversion.sourceHash,
                BLENDER_VERSION to conversion.blenderVersion,
                IMPORTED_AT to clock().toString(),
            ),
        ))
        conversion.stagingDir.deleteRecursively()
        val warnings = conversion.report.warnings() + stale.map {
            "${it.name} is no longer produced by ${blend.name} (action deleted in Blender?); kept so scenes using it still load"
        }
        return BlenderImportResult(blend, outputs, stale, warnings)
    }

    private fun stagingDir(blend: File): File {
        val rel = runCatching { blend.absoluteFile.relativeTo(projectDir.absoluteFile).invariantSeparatorsPath }.getOrDefault(blend.name)
        return File(projectDir, ".skate/cache/blender/" + sha256(rel.toByteArray()).take(16))
    }

    companion object {
        const val IMPORTER = "blender"
        const val REPORT_FILE = "report.json"
        const val ARGS_FILE = "args.json"
        const val OUTPUTS = "blender.outputs"
        const val SOURCE_HASH = "blender.sourceHash"
        const val BLENDER_VERSION = "blender.version"
        const val IMPORTED_AT = "blender.importedAt"

        fun sha256(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** Where a `.blend`'s generated files live and how they are named (AST-19 §4.1, §6). */
object BlenderOutputs {
    const val EXTENSION = "blend"
    const val DIR_SUFFIX = ".import"

    fun isBlend(file: File): Boolean = file.extension.equals(EXTENSION, ignoreCase = true)

    /** Blender's own side files: `x.blend1`..`x.blend32` backups and the `x.blend@` file a save writes first. */
    fun isBlenderSideFile(file: File): Boolean = AssetRegistry.isToolSideFile(file)

    fun dir(blend: File): File = File(blend.parentFile, blend.name + DIR_SUFFIX)

    fun baseName(blend: File): String = blend.nameWithoutExtension

    fun mainFileName(blend: File): String = baseName(blend) + ".glb"

    /** The model GLB a dropped or referenced `.blend` stands for, or null before its first import. */
    fun mainFile(blend: File): File? = File(dir(blend), mainFileName(blend)).takeIf { it.isFile }

    /** Output names recorded in the `.blend`'s sidecar by the last import. */
    fun recorded(meta: AssetMeta?): List<String> =
        meta?.settings?.get(BlenderImporter.OUTPUTS)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /** A plain file name (no folders) of a kind the script writes. */
    fun isSafeName(name: String): Boolean =
        name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != ".." &&
            (name.endsWith(".glb", ignoreCase = true) || name == BlenderImporter.REPORT_FILE)
}
