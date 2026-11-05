package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import java.io.File

/** What an export produces. */
enum class ExportTarget {
    /** `game.bin`, `manifest.json`, the standalone game jar and `run-game.bat`. */
    WINDOWS,

    /** Only `game.bin` and `manifest.json`, e.g. to refresh the data of an existing build. */
    PACK_ONLY,
}

/**
 * Export settings. [excludedScenes] are absolute scene paths left out of the pack (the default scene is always
 * included). [outputDir] defaults to `<project>/builds`.
 */
data class ExportOptions(
    val target: ExportTarget = ExportTarget.WINDOWS,
    val excludedScenes: Set<String> = emptySet(),
    val compress: Boolean = true,
    val onlyReferenced: Boolean = true,
    val outputDir: String? = null,
)

/** Export steps in order; [DONE] is reported once everything succeeded. */
enum class ExportStep { COLLECTING, PACKING, VERIFYING, COPYING_JAR, WRITING_LAUNCHER, DONE }

/** [fraction] is the completed part (0..1) of [step]; [detail] names the file being processed, if any. */
data class ExportProgress(val step: ExportStep, val fraction: Float = 0f, val detail: String = "") {
    /** Completed part of the whole export, 0..1. */
    val overall: Float
        get() = if (step == ExportStep.DONE) 1f
        else (step.ordinal + fraction.coerceIn(0f, 1f)) / ExportStep.DONE.ordinal
}

enum class ExportFailure {
    /** The output folder is the project folder or an existing file. */
    INVALID_OUTPUT,

    /** `gradlew gameJar` has not been run. */
    GAME_JAR_MISSING,

    /** `game.bin` does not match its manifest after writing. */
    VERIFY_FAILED,

    /** Reading the project or writing the build failed. */
    IO_ERROR,

    /** The editor could not save the project before exporting it. */
    SAVE_FAILED,
}

data class ExportResult(
    val outputDir: File,
    val failure: ExportFailure? = null,
    val detail: String = "",
    val assetCount: Int = 0,
    val packedBytes: Long = 0,
) {
    val success: Boolean get() = failure == null
}

/**
 * Exports a project to its output folder (`<project>/builds` by default): `game.bin` (referenced assets only,
 * compressed), `manifest.json` (checksums) and, for [ExportTarget.WINDOWS], the standalone game jar and
 * `run-game.bat`. The pack also carries the game string tables from [gameStringsDir] (see [AssetsPacker]).
 * [gameJarProvider] locates the jar produced by `gradlew gameJar`.
 */
class ProjectExporter(
    private val logger: LoggerService,
    gameStringsDir: File = File(Assets.Strings.GAME_STRINGS),
    private val gameJarProvider: () -> File? = ::findGameJar,
) {
    private val assetsPacker = AssetsPacker(logger, gameStringsDir)

    /** Runs the export on the calling thread, reporting each step to [onProgress]. */
    fun export(project: Project, options: ExportOptions = ExportOptions(), onProgress: (ExportProgress) -> Unit = {}): ExportResult {
        val outputDir = outputDir(project, options)
        val fail = { failure: ExportFailure, detail: String ->
            logger.logEngine("Export failed ($failure): $detail", LogLevel.ERROR)
            ExportResult(outputDir, failure, detail)
        }
        validateOutput(project, outputDir)?.let { return fail(ExportFailure.INVALID_OUTPUT, it) }
        val jar = if (options.target == ExportTarget.WINDOWS) {
            gameJarProvider()?.takeIf { it.isFile } ?: return fail(ExportFailure.GAME_JAR_MISSING, "Run `gradlew gameJar` first.")
        } else null

        return try {
            assetsPacker.pack(project, options.compress, options.onlyReferenced, outputDir, options.excludedScenes) { step, f, detail ->
                onProgress(ExportProgress(step, f, detail))
            }
            onProgress(ExportProgress(ExportStep.VERIFYING))
            val problems = verifyPack(outputDir)
            if (problems.isNotEmpty()) return fail(ExportFailure.VERIFY_FAILED, problems.joinToString("; "))

            if (jar != null) {
                onProgress(ExportProgress(ExportStep.COPYING_JAR))
                jar.copyTo(File(outputDir, GAME_JAR), overwrite = true)
                onProgress(ExportProgress(ExportStep.WRITING_LAUNCHER))
                generateBatchFile(outputDir)
            }
            val manifest = assetsPacker.lastManifest
            onProgress(ExportProgress(ExportStep.DONE, 1f))
            ExportResult(outputDir, assetCount = manifest?.entries?.size ?: 0, packedBytes = manifest?.binSize?.toLong() ?: 0)
        } catch (e: Exception) {
            fail(ExportFailure.IO_ERROR, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Scenes an export can include or leave out. */
    fun sceneFiles(project: Project, options: ExportOptions = ExportOptions()): List<File> =
        assetsPacker.sceneFiles(project, outputDir(project, options))

    private fun verifyPack(outputDir: File): List<String> {
        val manifest = assetsPacker.lastManifest ?: return listOf("no manifest")
        val bin = File(outputDir, AssetsPacker.BIN_FILE).readBytes()
        val headerSize = java.nio.ByteBuffer.wrap(bin, 0, Int.SIZE_BYTES).int + Int.SIZE_BYTES
        return PackBuilder.verify(bin.copyOfRange(headerSize, bin.size), manifest)
    }

    private fun generateBatchFile(outputDir: File) {
        File(outputDir, LAUNCHER).writeText("@ECHO OFF\r\ncd /d \"%~dp0\"\r\njava -jar $GAME_JAR\r\n")
    }

    companion object {
        const val GAME_JAR = "skate-game.jar"
        const val LAUNCHER = "run-game.bat"

        fun outputDir(project: Project, options: ExportOptions): File =
            options.outputDir?.takeIf { it.isNotBlank() }?.let(::File) ?: AssetsPacker.defaultOutputDir(project)

        /** Why [outputDir] cannot receive a build of [project], or null when it can. */
        fun validateOutput(project: Project, outputDir: File): String? {
            val projectDir = File(project.projectPath).parentFile.canonicalFile
            return when {
                outputDir.canonicalFile == projectDir -> "The output folder cannot be the project folder."
                outputDir.isFile -> "The output folder is a file: ${outputDir.path}"
                else -> null
            }
        }

        /** Looks for `*-game.jar` in the working directory's `build/libs` and next to the running jar. */
        fun findGameJar(): File? {
            val running = runCatching { File(ProjectExporter::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull()
            val dirs = listOfNotNull(File("build/libs"), running?.takeIf { it.isFile }?.parentFile)
            return dirs.flatMap { it.listFiles { f -> f.name.endsWith("-game.jar") }?.toList().orEmpty() }
                .maxByOrNull { it.lastModified() }
        }
    }
}
