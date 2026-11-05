package com.pafoid.skate.game

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.utils.AssetsPacker
import java.io.File

/**
 * Locates and reads the exported game's packed data (`game.bin`, AST-07). The game can be started from anywhere
 * (`run-game.bat`, a double-clicked jar, `java -jar` from another folder, `gradlew runGame`), so the pack is looked
 * up in this order:
 * 1. `-Dskate.bin=<path>` when set. It is then the only candidate, so a wrong path fails loudly instead of silently
 *    loading another pack.
 * 2. The folder of the running game jar (exports put `game.bin` next to `skate-game.jar`). When running from a
 *    classes directory (IDE/Gradle in development) there is no jar folder and this step is skipped.
 * 3. The working directory.
 */
object GameBin {
    /** System property that overrides the pack location: `-Dskate.bin=<path>`. */
    const val PATH_PROPERTY = "skate.bin"

    /** A located pack: where it was found (its folder also holds `game.settings.json`) and its bytes. */
    class Pack(val file: File, val bytes: ByteArray)

    /** Where the pack is looked for, in order, without duplicates. */
    fun candidates(
        override: String? = System.getProperty(PATH_PROPERTY),
        codeSource: File? = runningCodeSource(),
        workingDir: File = File("").absoluteFile,
    ): List<File> {
        override?.takeIf { it.isNotBlank() }?.let { return listOf(File(it).absoluteFile) }
        val jarDir = codeSource?.takeIf { it.isFile }?.absoluteFile?.parentFile
        return listOfNotNull(jarDir, workingDir).map { File(it, AssetsPacker.BIN_FILE).absoluteFile }.distinct()
    }

    /** The first existing candidate, or null. */
    fun resolve(candidates: List<File> = candidates()): File? = candidates.firstOrNull { it.isFile }

    /** Reads the first existing candidate, or logs every searched path as a game error and returns null. */
    fun read(logger: LoggerService, candidates: List<File> = candidates()): Pack? {
        val file = resolve(candidates)
        if (file == null) {
            logger.logGame(
                "Game data (${AssetsPacker.BIN_FILE}) not found. Searched: ${candidates.joinToString { it.path }}. " +
                    "Keep it next to the game jar or pass -D$PATH_PROPERTY=<path>.",
                LoggerService.LogLevel.ERROR,
            )
            return null
        }
        return runCatching { Pack(file, file.readBytes()) }
            .onFailure { logger.logError("Could not read game data ${file.path}", it, source = "game") }
            .getOrNull()
    }

    /** The jar (or classes directory) this game was loaded from, or null when the JVM does not say. */
    fun runningCodeSource(): File? =
        runCatching { File(GameBin::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull()
}
