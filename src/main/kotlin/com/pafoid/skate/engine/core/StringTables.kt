package com.pafoid.skate.engine.core

import java.io.File
import java.util.Properties

/**
 * String tables on disk. A source is either one table (`name.properties`) or a directory of per-area tables
 * (`<area>.properties`). Each table's translation sits next to it as `<area>_<locale>.properties`. Area names
 * must not end in `_xx`, which is reserved for the locale suffix.
 */
object StringTables {
    private val LOCALIZED = Regex("^.+_[a-z]{2}$")

    /** Default-language tables of [source], sorted by name (a single file is its own table). */
    fun baseTables(source: File): List<File> =
        if (source.isDirectory) {
            source.listFiles { f -> f.isFile && f.extension == "properties" && !isLocalized(f.nameWithoutExtension) }
                ?.sortedBy { it.name }.orEmpty()
        } else {
            listOf(source)
        }

    /** True for a translation's base name (`hud_fr`), false for a default-language table (`hud`). */
    fun isLocalized(nameWithoutExtension: String): Boolean = LOCALIZED.matches(nameWithoutExtension)

    /** File name of the [locale] translation of the table named [tableName] (`hud.properties` -> `hud_fr.properties`). */
    fun localizedName(tableName: String, locale: String): String {
        val dot = tableName.lastIndexOf('.')
        return if (dot < 0) "${tableName}_$locale" else "${tableName.substring(0, dot)}_$locale${tableName.substring(dot)}"
    }

    /** The [locale] translation of [table] (may not exist). */
    fun localized(table: File, locale: String): File = File(table.absoluteFile.parentFile, localizedName(table.name, locale))

    /** Reads [file] as UTF-8 (the encoding the tables are stored in). */
    fun read(file: File): Properties = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }

    /**
     * All tables of [source] merged, with their [locale] translations applied on top when given; the same
     * result [StringManager] builds.
     */
    fun merged(source: File, locale: String? = null): Properties = Properties().apply {
        baseTables(source).forEach { putAll(read(it)) }
        if (locale != null) baseTables(source).map { localized(it, locale) }.filter { it.exists() }.forEach { putAll(read(it)) }
    }

    /** Keys defined by more than one of [tables], mapped to the names of the files defining them. */
    fun duplicateKeys(tables: List<File>): Map<String, List<String>> =
        tables.flatMap { file -> read(file).stringPropertyNames().map { it to file.name } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
}
