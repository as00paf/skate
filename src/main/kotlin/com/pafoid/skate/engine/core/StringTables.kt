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

    /** Language part of a locale: `fr` for `fr`, `fr-CA`, `fr_CA` or `FR`. Translation files are per language. */
    fun languageOf(locale: String): String = locale.lowercase().substringBefore('_').substringBefore('-')

    /** Result of [layer]: the merged table, which sources had all their default tables, and duplicate keys. */
    class Layered(
        val table: Properties,
        val completeSources: Set<String>,
        /** True when at least one translation table was applied. */
        val translated: Boolean,
        /** Keys defined by several default tables of the same source, with those table names. */
        val duplicates: Map<String, List<String>>,
    )

    /**
     * The one merge rule ([StringManager] and [merged] both use it): for each source in order, its default tables,
     * then its [locale] translations. Later layers win, so missing translations fall back to the default language.
     * Unreadable tables are passed to [onError] and skipped.
     */
    fun layer(
        sources: List<StringSource>,
        locale: String?,
        onError: (table: String, source: String, error: Exception) -> Unit = { _, _, _ -> },
    ): Layered {
        val table = Properties()
        val complete = HashSet<String>()
        val duplicates = LinkedHashMap<String, List<String>>()
        var translated = false
        val language = locale?.let(::languageOf)
        fun read(source: StringSource, name: String): Properties? = try {
            source.read(name)
        } catch (e: Exception) {
            onError(name, source.label, e)
            null
        }
        for (source in sources) {
            val names = source.baseTables()
            val defaults = names.mapNotNull { name -> read(source, name)?.let { name to it } }
            if (names.isNotEmpty() && defaults.size == names.size) complete += source.label
            defaults.flatMap { (name, t) -> t.stringPropertyNames().map { it to name } }
                .groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }.forEach { (k, v) -> duplicates[k] = v }
            defaults.forEach { (_, t) -> table.putAll(t) }
            if (language != null) {
                names.mapNotNull { read(source, localizedName(it, language)) }.forEach {
                    table.putAll(it)
                    translated = true
                }
            }
        }
        return Layered(table, complete, translated, duplicates)
    }

    /** All tables of [source] merged, with their [locale] translations on top when given (see [layer]). */
    fun merged(source: File, locale: String? = null): Properties = layer(listOf(StringSource.files(source.path)), locale).table

    /** Keys defined by more than one of [tables], mapped to the names of the files defining them. */
    fun duplicateKeys(tables: List<File>): Map<String, List<String>> =
        tables.flatMap { file -> read(file).stringPropertyNames().map { it to file.name } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
}
