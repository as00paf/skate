package com.pafoid.skate.game

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.utils.AssetsPacker
import java.io.File

/** Locates and reads the exported game's packed data (`game.bin`, AST-07). */
object GameBin {
    /** System property that overrides the pack location: `-Dskate.bin=<path>`. */
    const val PATH_PROPERTY = "skate.bin"

    /** [override] when set, else [AssetsPacker.BIN_FILE] in the working directory (exports ship it next to the jar). */
    fun file(override: String? = System.getProperty(PATH_PROPERTY)): File =
        override?.takeIf { it.isNotBlank() }?.let(::File) ?: File(AssetsPacker.BIN_FILE)

    /** The pack's bytes, or null after logging an error when [file] is missing or unreadable. */
    fun read(logger: LoggerService, file: File = file()): ByteArray? {
        if (!file.isFile) {
            logger.logGame("Game data not found: ${file.absolutePath} (pass -D$PATH_PROPERTY=<path> to use another pack)", LoggerService.LogLevel.ERROR)
            return null
        }
        return runCatching { file.readBytes() }
            .onFailure { logger.logError("Could not read game data ${file.absolutePath}", it, source = "game") }
            .getOrNull()
    }
}
