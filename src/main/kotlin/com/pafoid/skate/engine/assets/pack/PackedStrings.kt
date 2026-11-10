package com.pafoid.skate.engine.assets.pack

import com.pafoid.skate.engine.core.StringSource
import java.io.File

/**
 * The game's string tables (`assets/Data/strings/game`: HUD, menus, showcase) shipped inside a pack. They are stored
 * under [PACK_DIR] with their file names, translations included, and read back as a [StringSource].
 */
object PackedStrings {
    /** Pack path of the game string tables (`strings/game/hud.properties`, `strings/game/hud_fr.properties`, ...). */
    const val PACK_DIR = "strings/game"

    /** Pack type of the tables, matching `FileType.TEXT`. */
    const val TYPE = "TEXT"

    /** Every `.properties` table of [sourceDir] (base tables and translations) as pack inputs, sorted by name. */
    fun inputs(sourceDir: File): List<PackInput> =
        sourceDir.listFiles { f -> f.isFile && f.extension == "properties" }.orEmpty()
            .sortedBy { it.name }
            .map { PackInput(it.absolutePath, "$PACK_DIR/${it.name}", TYPE, it.readBytes()) }

    /** True when [packPath] is one of the game string tables (directly under [PACK_DIR]). */
    fun isTable(packPath: String): Boolean =
        packPath.startsWith("$PACK_DIR/") && '/' !in packPath.removePrefix("$PACK_DIR/") && packPath.endsWith(".properties")

    /** The packed tables as a [StringSource]; [tables] maps pack paths to their bytes, other paths are ignored. */
    fun source(tables: Map<String, ByteArray>): StringSource =
        StringSource.memory("pack:$PACK_DIR", tables.filterKeys(::isTable).mapKeys { it.key.substringAfterLast('/') })
}
