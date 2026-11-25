package com.pafoid.skate.editor.assetstore.convert

import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.assetstore.StorePaths
import com.pafoid.skate.editor.tools.external.BlenderInstall
import com.pafoid.skate.editor.tools.external.BlenderLocator
import com.pafoid.skate.editor.tools.external.ProcessRunner
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import com.pafoid.skate.editor.tools.external.CancelToken as ProcessCancelToken

/** How a USD → glTF conversion ended (AST-16 §8). */
sealed interface ConversionResult {
    /**
     * [outputs] are paths relative to the output folder, already checked to stay inside it; [main] is the `.gltf`.
     * [warnings] are for the user (materials that fell back to defaults, missing textures).
     */
    data class Converted(val main: String, val outputs: List<String>, val warnings: List<String>, val tool: String) : ConversionResult

    data class Failed(val message: String, val log: String = "") : ConversionResult
}

/** Converts OpenUSD to glTF once, at import time, so the runtime never sees USD (AST-16 §8). */
interface UsdConverter {
    /** Shown in the ledger: `converted USD -> glTF via <id>`. */
    val id: String

    /** Blocking (may probe for an external tool): call it on an IO worker. */
    fun isAvailable(): Boolean

    /** Converts [input] into the empty folder [outDir] as `<name>.gltf` plus its `.bin` and textures. Blocking. */
    fun run(input: File, outDir: File, name: String, cancel: CancelToken): ConversionResult

    companion object {
        val EXTENSIONS = setOf("usd", "usda", "usdc", "usdz")

        fun isUsd(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in EXTENSIONS
    }
}

/** `report.json` written by `skate_usd_to_gltf.py`. */
@Serializable
data class UsdConversionReport(
    val blender: String = "",
    val main: String = "",
    val meshes: Int = 0,
    val simplifiedMaterials: List<SimplifiedMaterial> = emptyList(),
    val missingImages: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
) {
    @Serializable
    data class SimplifiedMaterial(val material: String, val nodes: List<String> = emptyList())

    fun warnings(): List<String> =
        simplifiedMaterials.map { "Material '${it.material}' has no USD preview surface Skate can read; it imports with default PBR values" } +
            missingImages.map { "Missing texture: $it" }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): UsdConversionReport = json.decodeFromString(serializer(), text)
    }
}

/**
 * Headless Blender (AST-16 §8, option 1): Blender's USD importer, then its glTF exporter (metres, Y-up), through the
 * bundled `skate_usd_to_gltf.py`. Shares the Blender locator and process runner with AST-19. Blender reads
 * `UsdPreviewSurface` materials; MDL-only materials fall back to defaults and are reported as warnings.
 */
class BlenderUsdConverter(
    private val runner: ProcessRunner,
    private val blender: () -> BlenderInstall?,
    private val script: () -> File,
    private val timeout: Duration = Duration.ofMinutes(10),
) : UsdConverter {
    override val id = "Blender"

    override fun isAvailable(): Boolean = blender() != null

    override fun run(input: File, outDir: File, name: String, cancel: CancelToken): ConversionResult {
        if (!input.isFile) return ConversionResult.Failed("USD file not found: ${input.name}")
        val install = blender() ?: return ConversionResult.Failed(NEEDS_BLENDER)
        outDir.deleteRecursively()
        outDir.mkdirs()
        // A file, not an argument: Windows command lines mangle the quotes inside JSON. Kept out of outDir.
        val argsFile = File(outDir.parentFile, "${outDir.name}.args.json").apply { writeText(arguments(input, outDir, name)) }
        val result = runBridged(command(install.executable, script(), argsFile), outDir.parentFile, cancel)
        if (result.cancelled) return ConversionResult.Failed("Conversion cancelled")
        if (result.timedOut) return ConversionResult.Failed("Blender timed out after ${timeout.seconds} s", result.tail())
        if (!result.isSuccess) return ConversionResult.Failed("Blender exited with code ${result.exitCode}", result.tail())
        return collect(outDir, name, result.tail())
    }

    /** Runs Blender with the store's [cancel] mirrored into the runner's own token (polled every 100 ms). */
    private fun runBridged(command: List<String>, workingDir: File, cancel: CancelToken): com.pafoid.skate.editor.tools.external.ProcessResult {
        val processCancel = ProcessCancelToken()
        val done = AtomicBoolean(false)
        val bridge = Thread({
            while (!done.get()) {
                if (cancel.isCancelled) { processCancel.cancel(); return@Thread }
                try { Thread.sleep(100) } catch (_: InterruptedException) { return@Thread }
            }
        }, "usd-convert-cancel").apply { isDaemon = true; start() }
        try {
            return runner.run(command, workingDir, timeout, processCancel)
        } finally {
            done.set(true)
            bridge.interrupt()
        }
    }

    companion object {
        const val SCRIPT_RESOURCE = "/blender/skate_usd_to_gltf.py"
        const val REPORT_FILE = "report.json"
        const val NEEDS_BLENDER = "Blender 4.2+ is needed to convert USD; set its path in Editor Settings"

        /**
         * Background, factory settings (no user add-ons), Python errors become a non-zero exit. No `.blend` is opened
         * and never `-y`/`--enable-autoexec` (AST-19 §3): only our script runs.
         */
        fun command(blender: File, script: File, argsFile: File): List<String> =
            listOf(blender.path, "-b", "--factory-startup", "--python-exit-code", "1", "--python", script.path, "--", argsFile.path)

        fun arguments(input: File, outDir: File, name: String): String = buildJsonObject {
            put("input", input.absolutePath)
            put("out_dir", outDir.absolutePath)
            put("name", name)
        }.toString()

        /**
         * Reads the report and checks every produced file (AST-16 §8): regular files only (no symlinks), each path
         * strictly inside [outDir] and accepted by [StorePaths.safeRelative], and the main `.gltf` present.
         */
        fun collect(outDir: File, name: String, log: String = ""): ConversionResult {
            val report = runCatching { UsdConversionReport.decode(File(outDir, REPORT_FILE).readText()) }
                .getOrElse { return ConversionResult.Failed("Blender wrote no conversion report", log) }
            val outputs = ArrayList<String>()
            for (file in outDir.walkTopDown().filter { it != outDir }) {
                if (Files.isSymbolicLink(file.toPath())) return ConversionResult.Failed("Converter produced a link: ${file.name}", log)
                if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) continue
                val relative = StorePaths.relativeInside(outDir, file)?.let(StorePaths::safeRelative)
                    ?: return ConversionResult.Failed("Converter output escapes its folder: ${file.name}", log)
                if (relative != REPORT_FILE) outputs += relative
            }
            val main = "$name.gltf"
            if (main !in outputs || File(outDir, main).length() == 0L) return ConversionResult.Failed("Blender produced no $main", log)
            return ConversionResult.Converted(main, outputs.sorted(), report.warnings(), "Blender ${report.blender}".trim())
        }

        /** Copies the bundled script to a temp file once per editor session. */
        fun extractScript(): File {
            val file = Files.createTempFile("skate_usd_to_gltf", ".py").toFile().apply { deleteOnExit() }
            val stream = BlenderUsdConverter::class.java.getResourceAsStream(SCRIPT_RESOURCE) ?: error("Missing $SCRIPT_RESOURCE")
            stream.use { input -> file.outputStream().use { input.copyTo(it) } }
            return file
        }
    }
}

/**
 * The Blender install for the store, following the Editor Settings path: the locator is rebuilt when the path changes.
 * [best] is blocking (runs `blender --version` on first use); call it on an IO worker.
 */
class StoreBlender(private val runner: ProcessRunner, private val path: () -> String?) {
    private var locator: BlenderLocator? = null
    private var locatorPath: String? = null

    @Synchronized
    fun best(): BlenderInstall? {
        val current = path()
        if (locator == null || current != locatorPath) {
            locator = BlenderLocator(runner, setting = { current })
            locatorPath = current
        }
        return locator!!.best()
    }
}
