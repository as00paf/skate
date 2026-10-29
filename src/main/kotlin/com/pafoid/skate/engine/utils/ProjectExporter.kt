package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import java.io.File

/**
 * Exports a project to `<project>/builds`: `game.bin` (referenced assets only, compressed), `manifest.json`
 * (checksums), the standalone game jar and `run-game.bat`.
 * [gameJarProvider] locates the jar produced by `gradlew gameJar`.
 */
class ProjectExporter(
    private val logger: LoggerService,
    private val gameJarProvider: () -> File? = ::findGameJar,
) {
    private val assetsPacker = AssetsPacker(logger)

    fun export(project: Project, compress: Boolean = true, onlyReferenced: Boolean = true): Boolean {
        if (!assetsPacker.pack(project, compress, onlyReferenced)) return false
        val outputDir = File(File(project.projectPath).parentFile, AssetsPacker.BUILDS_DIR)
        if (!verifyPack(outputDir)) return false
        if (!copyGameJar(outputDir)) return false
        return generateBatchFiles(outputDir)
    }

    private fun verifyPack(outputDir: File): Boolean {
        val manifest = assetsPacker.lastManifest ?: return false
        val bin = File(outputDir, AssetsPacker.BIN_FILE).readBytes()
        val headerSize = java.nio.ByteBuffer.wrap(bin, 0, Int.SIZE_BYTES).int + Int.SIZE_BYTES
        val problems = PackBuilder.verify(bin.copyOfRange(headerSize, bin.size), manifest)
        problems.forEach { logger.logEngine("Export verification failed: $it", LogLevel.ERROR) }
        return problems.isEmpty()
    }

    private fun copyGameJar(outputDir: File): Boolean {
        val jar = gameJarProvider()
        if (jar == null || !jar.isFile) {
            logger.logEngine("Game jar not found. Run `gradlew gameJar` first.", LogLevel.ERROR)
            return false
        }
        jar.copyTo(File(outputDir, GAME_JAR), overwrite = true)
        return true
    }

    private fun generateBatchFiles(outputDir: File): Boolean {
        val winBatFile = File(outputDir, "run-game.bat")
        winBatFile.writeText("@ECHO OFF\r\ncd /d \"%~dp0\"\r\njava -jar $GAME_JAR\r\n")
        return winBatFile.exists()
    }

    companion object {
        const val GAME_JAR = "skate-game.jar"

        /** Looks for `*-game.jar` in the working directory's `build/libs` and next to the running jar. */
        fun findGameJar(): File? {
            val running = runCatching { File(ProjectExporter::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull()
            val dirs = listOfNotNull(File("build/libs"), running?.takeIf { it.isFile }?.parentFile)
            return dirs.flatMap { it.listFiles { f -> f.name.endsWith("-game.jar") }?.toList().orEmpty() }
                .maxByOrNull { it.lastModified() }
        }
    }
}
