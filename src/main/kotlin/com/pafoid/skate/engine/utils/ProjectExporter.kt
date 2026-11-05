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

/**
 * Why an export failed, as a string key of the editor's `assets` area (`assets/strings/editor/assets.properties`)
 * so the export wizard can show it in the editor language. [ExportResult.detailArgs] fill its `%s` placeholders.
 */
enum class ExportDetail(val key: String) {
    /** Args: output folder. */
    OUTPUT_IS_PROJECT("lbl.export.detail.output_is_project"),

    /** Args: output folder. */
    OUTPUT_IS_FILE("lbl.export.detail.output_is_file"),

    /** Args: the folders and files searched, `; `-separated. */
    GAME_JAR_NOT_BUILT("lbl.export.detail.game_jar_not_built"),

    /** Args: mismatching entries, `; `-separated. */
    PACK_MISMATCH("lbl.export.detail.pack_mismatch"),

    /** Args: the system's error message. */
    IO_FAILURE("lbl.export.detail.io_failure"),

    /** No args: the editor has no open project. */
    NO_PROJECT("lbl.export.no_project"),
}

/** [detail] and [detailArgs] explain a [failure] (see [ExportDetail]); args are raw paths or system messages. */
data class ExportResult(
    val outputDir: File,
    val failure: ExportFailure? = null,
    val detail: ExportDetail? = null,
    val detailArgs: List<String> = emptyList(),
    val assetCount: Int = 0,
    val packedBytes: Long = 0,
) {
    val success: Boolean get() = failure == null
}

/**
 * Exports a project to its output folder (`<project>/builds` by default): `game.bin` (referenced assets only,
 * compressed), `manifest.json` (checksums) and, for [ExportTarget.WINDOWS], the standalone game jar and
 * `run-game.bat`. The pack also carries the game string tables from [gameStringsDir] (see [AssetsPacker]).
 * [gameJarSearch] locates the jar produced by `gradlew gameJar` (see [GameJarLocator]).
 */
class ProjectExporter(
    private val logger: LoggerService,
    gameStringsDir: File = File(Assets.Strings.GAME_STRINGS),
    private val gameJarSearch: () -> GameJarSearch = { GameJarLocator().locate() },
) {
    private val assetsPacker = AssetsPacker(logger, gameStringsDir)

    /** Runs the export on the calling thread, reporting each step to [onProgress]. */
    fun export(project: Project, options: ExportOptions = ExportOptions(), onProgress: (ExportProgress) -> Unit = {}): ExportResult {
        val outputDir = outputDir(project, options)
        val fail = { failure: ExportFailure, detail: ExportDetail, args: List<String> ->
            logger.logEngine("Export failed ($failure, $detail): ${args.joinToString()}", LogLevel.ERROR)
            ExportResult(outputDir, failure, detail, args)
        }
        validateOutput(project, outputDir)?.let { return fail(ExportFailure.INVALID_OUTPUT, it, listOf(outputDir.path)) }
        val jar = if (options.target == ExportTarget.WINDOWS) {
            val search = gameJarSearch()
            if (search.stale) logger.logEngine("Game jar ${search.jar} is older than the editor build; run `gradlew gameJar`", LogLevel.WARN)
            search.jar?.takeIf { it.isFile }
                ?: return fail(ExportFailure.GAME_JAR_MISSING, ExportDetail.GAME_JAR_NOT_BUILT, listOf(searchedList(search)))
        } else null

        return try {
            assetsPacker.pack(project, options.compress, options.onlyReferenced, outputDir, options.excludedScenes) { step, f, detail ->
                onProgress(ExportProgress(step, f, detail))
            }
            onProgress(ExportProgress(ExportStep.VERIFYING))
            val problems = verifyPack(outputDir)
            if (problems.isNotEmpty()) return fail(ExportFailure.VERIFY_FAILED, ExportDetail.PACK_MISMATCH, listOf(problems.joinToString("; ")))

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
            fail(ExportFailure.IO_ERROR, ExportDetail.IO_FAILURE, listOf(e.message ?: e.javaClass.simpleName))
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
        fun validateOutput(project: Project, outputDir: File): ExportDetail? {
            val projectDir = File(project.projectPath).parentFile.canonicalFile
            return when {
                outputDir.canonicalFile == projectDir -> ExportDetail.OUTPUT_IS_PROJECT
                outputDir.isFile -> ExportDetail.OUTPUT_IS_FILE
                else -> null
            }
        }

        /** [GameJarSearch.searched] as one line for messages (`;`-separated, or "-" when nothing was searched). */
        fun searchedList(search: GameJarSearch): String = search.searched.joinToString("; ") { it.path }.ifEmpty { "-" }
    }
}
