package com.pafoid.skate.engine.core

import java.io.File
import java.util.Properties

/**
 * A set of string tables (see [StringTables] for the naming rules): files on disk, or tables read out of a
 * game pack. [StringManager] loads the base tables, then the `<table>_<locale>` translations on top.
 */
interface StringSource {
    /** Shown in logs, and used to avoid adding the same source twice. */
    val label: String

    /** Names of the default-language tables, sorted. */
    fun baseTables(): List<String>

    /** Table [name] (a base table or a translation), or null when it does not exist. */
    fun read(name: String): Properties?

    companion object {
        /** One `.properties` file, or a directory of per-area tables. */
        fun files(path: String): StringSource = FileStringSource(path)

        /** Tables already in memory, keyed by file name (`hud.properties`, `hud_fr.properties`), stored as UTF-8. */
        fun memory(label: String, tables: Map<String, ByteArray>): StringSource = MemoryStringSource(label, tables)
    }
}

private class FileStringSource(path: String) : StringSource {
    private val source = File(path)
    override val label = path
    private val dir get() = if (source.isDirectory) source else source.absoluteFile.parentFile

    override fun baseTables() = StringTables.baseTables(source).filter { it.isFile }.map { it.name }

    override fun read(name: String): Properties? = File(dir, name).takeIf { it.isFile }?.let(StringTables::read)
}

private class MemoryStringSource(override val label: String, private val tables: Map<String, ByteArray>) : StringSource {
    override fun baseTables() = tables.keys
        .filter { it.endsWith(".properties") && !StringTables.isLocalized(it.removeSuffix(".properties")) }
        .sorted()

    override fun read(name: String): Properties? =
        tables[name]?.let { bytes -> Properties().apply { bytes.inputStream().reader(Charsets.UTF_8).use { load(it) } } }
}
