package com.pafoid.skate.editor.assetimport.blender

import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.tools.external.BlenderAvailability
import com.pafoid.skate.editor.tools.external.BlenderLocator
import com.pafoid.skate.editor.tools.external.BlenderTestResult
import com.pafoid.skate.editor.tools.external.JdkProcessRunner
import com.pafoid.skate.editor.tools.external.ProcessRunner
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import java.io.File
import java.nio.file.Files
import java.time.Duration

/**
 * Keeps `.blend` files under the open project's `Assets/` imported (AST-19 §5): scans once a second on an IO worker,
 * imports new files and re-imports saved ones through [BlenderImportQueue], logs the result and announces the new
 * files (`FileSystemChangedEvent`, so the Asset Browser and Project window refresh; a loaded GLB hot-reloads).
 * Also serves the `.blend` inspector and the Editor Settings Blender section (EDT-66): import settings, forced
 * re-import ([reimport], also on [FileSystemEvent.ReimportRequested]), Blender availability and the Test button.
 * Call [update] every editor frame.
 */
class BlenderSync(
    private val engine: Engine,
    private val projectManager: ProjectManager,
    private val settings: () -> EditorSettings,
    private val runner: ProcessRunner = JdkProcessRunner(),
) {
    private val locator = BlenderLocator(runner, setting = { settings().blenderPath })
    private val watch = BlendWatch()
    private val scriptFile: File by lazy { extractScript() }

    private var project: File? = null
    private var queue: BlenderImportQueue? = null
    private var firstScan = true
    private var scanning = false
    private var lastScanNanos = 0L
    private var lastBlenderPath: String? = null

    private val logger get() = engine.logger

    /** Last result of looking for Blender; checked again when the Blender path setting changes. */
    var availability: BlenderAvailability = BlenderAvailability.Unknown
        private set

    init {
        engine.eventSystem.subscribe<FileSystemEvent.ReimportRequested> { event ->
            val file = File(event.path)
            if (BlenderOutputs.isBlend(file)) reimport(file)
        }
    }

    fun update(nowNanos: Long = System.nanoTime()) {
        if (settings().blenderPath != lastBlenderPath) {
            lastBlenderPath = settings().blenderPath
            locator.invalidate()
            availability = BlenderAvailability.Unknown
        }
        if (availability == BlenderAvailability.Unknown) checkAvailability()
        val dir = projectManager.getProjectDirectory()?.absoluteFile
        if (dir != project) switchProject(dir)
        val root = dir ?: return
        if (scanning || nowNanos - lastScanNanos < SCAN_INTERVAL_NANOS) return
        lastScanNanos = nowNanos
        scanning = true
        val assets = ProjectLayout.assetsDir(root)
        engine.jobSystem.runIO {
            val stamps = runCatching { BlendWatch.scan(assets) }.getOrDefault(emptyMap())
            engine.jobSystem.runOnMain { onScan(root, stamps) }
        }
    }

    private fun onScan(root: File, stamps: Map<File, BlendWatch.Stamp>) {
        scanning = false
        if (root != project) return
        if (firstScan) {
            firstScan = false
            if (!settings().blenderImportOnOpen) {
                watch.seed(stamps)
                return
            }
        }
        for (blend in watch.observe(stamps)) {
            val auto = AssetMeta.read(blend)?.settings?.let(BlenderImportSettings::from)?.autoReimport ?: true
            if (auto) queue?.request(blend)
        }
    }

    /** Re-imports [blend] now, even when it has not changed (inspector button, Project window menu). */
    fun reimport(blend: File) {
        queue?.request(blend, force = true)
    }

    fun isImporting(blend: File): Boolean = queue?.isImporting(blend) == true

    /** The import settings stored in [blend]'s sidecar (defaults when it has none yet). */
    fun settingsOf(blend: File): BlenderImportSettings =
        BlenderImportSettings.from(AssetMeta.read(blend)?.settings.orEmpty())

    fun lastImport(blend: File): BlenderImportRecord? = BlenderImportRecord.from(AssetMeta.read(blend))

    /** Writes [value] into [blend]'s sidecar (created with a GUID when missing). False when no project is open. */
    fun saveSettings(blend: File, value: BlenderImportSettings): Boolean {
        val registry = projectManager.assetRegistry() ?: return false
        val meta = AssetMeta.read(blend) ?: run {
            registry.register(blend)
            AssetMeta.read(blend)
        } ?: return false
        AssetMeta.write(blend, value.applyTo(meta))
        return true
    }

    /**
     * Editor Settings "Test" (EDT-66): looks for Blender with [path] as the setting, on an IO worker (it runs
     * `blender --version`), and hands the result to [onResult] on the main thread. Does not change the settings.
     */
    fun test(path: String?, onResult: (BlenderTestResult) -> Unit) {
        engine.jobSystem.runIO {
            val best = runCatching { BlenderLocator(runner, setting = { path }).best() }.getOrNull()
            val result = BlenderTestResult.of(path, best)
            engine.jobSystem.runOnMain { onResult(result) }
        }
    }

    private fun checkAvailability() {
        availability = BlenderAvailability.Checking
        val path = settings().blenderPath
        engine.jobSystem.runIO {
            val best = runCatching { locator.best() }.getOrNull()
            engine.jobSystem.runOnMain {
                // A path change while probing restarts the check from update().
                if (path == settings().blenderPath && availability == BlenderAvailability.Checking) {
                    availability = best?.let(BlenderAvailability::Found) ?: BlenderAvailability.Missing
                }
            }
        }
    }

    private fun switchProject(dir: File?) {
        project = dir
        watch.clear()
        firstScan = true
        queue = dir?.let { root ->
            val registry = projectManager.assetRegistry() ?: return@let null
            val importer = BlenderImporter(
                root, registry, runner, blender = { locator.best() }, script = { scriptFile },
                timeout = { Duration.ofSeconds(settings().blenderTimeoutSeconds.toLong().coerceAtLeast(10)) },
            )
            BlenderImportQueue(
                io = { job -> engine.jobSystem.runIO { job() } },
                main = { job -> engine.jobSystem.runOnMain { job() } },
                convert = { blend, force -> importer.convert(blend, force) },
                apply = importer::apply,
                onOutcome = ::report,
            )
        }
    }

    private fun report(outcome: BlenderImportOutcome) {
        when (outcome) {
            is BlenderImportOutcome.UpToDate -> Unit
            is BlenderImportOutcome.Imported -> {
                val result = outcome.result
                logger.logEditor("Imported ${result.blend.name}: ${result.outputs.filter { it.extension == "glb" }.joinToString { it.name }}")
                result.warnings.forEach { logger.logEditor("${result.blend.name}: $it", LogLevel.WARN) }
                engine.eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(BlenderOutputs.dir(result.blend).path))
            }
            is BlenderImportOutcome.Failed -> {
                logger.logEditor("Blender import of ${outcome.blend.name} failed: ${outcome.reason} (previous files kept)", LogLevel.ERROR)
                if (outcome.log.isNotBlank()) logger.logEditor(outcome.log, LogLevel.ERROR)
            }
        }
    }

    private fun extractScript(): File {
        val file = Files.createTempFile("skate_export", ".py").toFile().apply { deleteOnExit() }
        val stream = BlenderSync::class.java.getResourceAsStream(SCRIPT_RESOURCE) ?: error("Missing $SCRIPT_RESOURCE")
        stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    companion object {
        const val SCRIPT_RESOURCE = "/blender/skate_export.py"
        private const val SCAN_INTERVAL_NANOS = 1_000_000_000L
    }
}
