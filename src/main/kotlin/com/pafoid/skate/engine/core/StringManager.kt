package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.core.LoggerService.LogLevel
import java.io.File
import java.util.*

class StringManager(
    private val logger: LoggerService,
    private var currentLocale: String = "en"
) {
    private var currentPath = ""
    private var basePath = ""
    private val properties = Properties()
    private val extraPaths = mutableListOf<String>()
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
        basePath = path
        return loadSource(path, null)
    }

    /** Merges another strings source (e.g. the game's tables) into the loaded ones; reloaded per locale by [setLocale]. */
    fun addStrings(path: String): Boolean {
        if (path !in extraPaths) extraPaths.add(path)
        return loadSource(path, null)
    }

    /** Loads every table of [path]; with a [locale], only their existing translations. */
    private fun loadSource(path: String, locale: String?): Boolean {
        val tables = StringTables.baseTables(File(path))
        if (tables.isEmpty()) {
            logger.log("No string tables found at $path", LogLevel.ERROR, source = "editor")
            return false
        }
        if (locale == null) {
            StringTables.duplicateKeys(tables).forEach { (key, files) ->
                logger.log("String key $key is defined in several tables: $files", LogLevel.WARN, source = "editor")
            }
            return tables.map { loadFile(it.path) }.all { it }
        }
        return tables.map { loadFile(StringTables.localized(it, locale).path, optional = true) }.any { it }
    }

    private fun loadFile(path: String, optional: Boolean = false): Boolean {
        currentPath = path
        if (optional && !File(path).exists()) return false
        try {
            File(path).reader(Charsets.UTF_8).use { properties.load(it) }
        } catch (e: Exception) {
            logger.log(
                "Failed to load strings for baseName $path and locale $currentLocale",
                LogLevel.ERROR, source = "editor", throwable = e
            )
            return false
        }
        return true
    }

    fun setLocale(locale: String): Boolean {
        if (currentLocale != locale) {
            currentLocale = locale
            properties.clear() // Clear existing properties before reloading
            // Default strings first so keys missing from a translation fall back instead of showing !!key!!
            if (basePath.isNotEmpty()) loadSource(basePath, null)
            extraPaths.forEach { loadSource(it, null) }
            val loaded = basePath.isNotEmpty() && loadSource(basePath, locale)
            extraPaths.forEach { loadSource(it, locale) }
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
