package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.core.LoggerService.LogLevel
import java.util.*

class StringManager(
    private val logger: LoggerService,
    private var currentLocale: String = "en"
) {
    private var base: StringSource? = null
    private val properties = Properties()
    private val extraSources = mutableListOf<StringSource>()
    private val localeListeners = mutableListOf<(String) -> Unit>()

    val locale: String get() = currentLocale

    /** Notified after [setLocale] changed the active locale, so UI can refresh text and fonts. */
    fun addLocaleListener(listener: (String) -> Unit) {
        localeListeners.add(listener)
    }

    /**
     * Loads the default strings from [path]: one table, or a directory of per-area tables merged together
     * (see [StringTables]). Translations for the current locale are applied by [setLocale].
     */
    fun loadStrings(path: String): Boolean {
        val source = StringSource.files(path)
        base = source
        return loadSource(source, null)
    }

    /** Merges another strings source (e.g. the game's tables) into the loaded ones; reloaded per locale by [setLocale]. */
    fun addStrings(path: String): Boolean = addStrings(StringSource.files(path))

    /** Merges [source] (e.g. game tables read from a pack) into the loaded ones; reloaded per locale by [setLocale]. */
    fun addStrings(source: StringSource): Boolean {
        extraSources.removeAll { it.label == source.label }
        extraSources.add(source)
        val loaded = loadSource(source, null)
        loadSource(source, currentLocale) // the active translation, if the source has one
        return loaded
    }

    /** Loads every base table of [source]; with a [locale], only their existing translations. */
    private fun loadSource(source: StringSource, locale: String?): Boolean {
        val tables = source.baseTables()
        if (tables.isEmpty()) {
            if (locale == null) logger.log("No string tables found at ${source.label}", LogLevel.ERROR, source = "editor")
            return false
        }
        val names = if (locale == null) tables else tables.map { StringTables.localizedName(it, locale) }
        val loaded = names.mapNotNull { name -> readTable(source, name)?.let { name to it } }
        if (locale == null) {
            loaded.flatMap { (name, table) -> table.stringPropertyNames().map { it to name } }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }
                .forEach { (key, files) ->
                    logger.log("String key $key is defined in several tables: $files", LogLevel.WARN, source = "editor")
                }
        }
        loaded.forEach { (_, table) -> properties.putAll(table) }
        return if (locale == null) loaded.size == tables.size else loaded.isNotEmpty()
    }

    private fun readTable(source: StringSource, name: String): Properties? = try {
        source.read(name)
    } catch (e: Exception) {
        logger.log(
            "Failed to load strings table $name from ${source.label} (locale $currentLocale)",
            LogLevel.ERROR, source = "editor", throwable = e
        )
        null
    }

    fun setLocale(locale: String): Boolean {
        if (currentLocale != locale) {
            currentLocale = locale
            properties.clear() // Clear existing properties before reloading
            // Default strings first so keys missing from a translation fall back instead of showing !!key!!
            base?.let { loadSource(it, null) }
            extraSources.forEach { loadSource(it, null) }
            val loaded = base?.let { loadSource(it, locale) } ?: false
            extraSources.forEach { loadSource(it, locale) }
            localeListeners.toList().forEach { it(locale) }
            return loaded
        }
        return false
    }

    fun getString(key: String): String {
        val value = properties.getProperty(key)
        if (value == null) {
            logger.logEditor("Missing string key: $key")
        }
        return value ?: "!!${key}!!"
    }

    fun getString(key: String, vararg formatArgs: Any): String {
        val formatString = properties.getProperty(key)
        if (formatString == null) {
            logger.logEditor("Missing string key: $key")
        }
        val fmt = formatString ?: "!!${key}!!"
        return String.format(fmt, *formatArgs)
    }

    /**
     * Picks `key.<category>` by the current locale's plural rule (`zero` wins for 0 when defined), falling back to
     * `key.other`. The first format argument is the quantity.
     */
    fun getQuantityString(key: String, quantity: Int, vararg formatArgs: Any): String {
        val candidates = listOf(
            if (quantity == 0) "$key.zero" else null,
            "$key.${PluralRules.category(currentLocale, quantity).suffix}",
            "$key.other",
        )
        val resourceKey = candidates.filterNotNull().firstOrNull { properties.containsKey(it) } ?: "$key.other"
        val formatString = properties.getProperty(resourceKey, "!!${resourceKey}!!")
        return String.format(formatString, quantity, *formatArgs)
    }
}
