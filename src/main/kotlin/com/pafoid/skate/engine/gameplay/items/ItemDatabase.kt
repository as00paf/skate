package com.pafoid.skate.engine.gameplay.items

import com.pafoid.skate.engine.gameplay.CharacterConfigs
import java.io.File
import java.util.logging.Logger

/**
 * Item definitions by id (GPL-24 §3.1). Files are decoded leniently (unknown keys ignored, missing ones defaulted).
 * A later definition with the same id replaces the earlier one with a warning, so project files loaded after the
 * bundled defaults override them. Unknown ids return null, never throw.
 */
class ItemDatabase {
    private val items = LinkedHashMap<String, ItemDef>()
    private val logger = Logger.getLogger("ItemDatabase")

    /** Warnings from the last loads (duplicates, invalid files), for tests and the console. */
    val warnings = mutableListOf<String>()

    val all: Collection<ItemDef> get() = items.values

    operator fun get(id: String): ItemDef? = items[id]

    fun clear() {
        items.clear()
        warnings.clear()
    }

    fun register(def: ItemDef) {
        if (def.id.isBlank()) return warn("Item without an id ignored")
        if (items.put(def.id, def) != null) warn("Duplicate item id '${def.id}': the last definition wins")
    }

    /** Decodes one `*.item.json` text read from [path]; returns the item, or null (with a warning) when invalid. */
    fun load(path: String, text: String): ItemDef? = try {
        CharacterConfigs.json.decodeFromString(ItemDef.serializer(), text).also(::register)
    } catch (e: Exception) {
        warn("Invalid item file '$path': ${e.message}")
        null
    }

    /** Loads every `*.item.json` under [dir] (sorted, so the result does not depend on the file system order). */
    fun loadFolder(dir: File): Int {
        if (!dir.isDirectory) return 0
        return dir.walkTopDown().filter { it.isFile && it.name.endsWith(EXTENSION) }.sortedBy { it.path }
            .count { load(it.path, it.readText()) != null }
    }

    /** Loads [paths] through [read] (the engine's assets or the classpath); unreadable paths are reported. */
    fun loadAll(paths: List<String>, read: (String) -> String? = CharacterConfigs::readText): Int = paths.count { path ->
        val text = read(path)
        if (text == null) warn("Item file '$path' not found")
        text != null && load(path, text) != null
    }

    private fun warn(message: String) {
        warnings += message
        logger.warning(message)
    }

    companion object {
        const val EXTENSION = ".item.json"
    }
}
