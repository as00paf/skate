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

    fun loadStrings(path: String): Boolean {
        basePath = path
        return loadFile(path)
    }

    /** Merges another strings file (e.g. the game's) into the loaded ones; it is reloaded per locale by [setLocale]. */
    fun addStrings(path: String): Boolean {
        if (path !in extraPaths) extraPaths.add(path)
        return loadFile(path)
    }

    private fun localized(path: String, locale: String): String {
        val file = File(path)
        return file.absolutePath.replace(".${file.extension}", "_$locale.${file.extension}")
    }

    private fun loadFile(path: String, optional: Boolean = false): Boolean {
        currentPath = path
        if (optional && !File(path).exists()) return false
        try {
            val inputFile = File(path)
            inputFile.inputStream().use {
                properties.load(it)
            }
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
            if (basePath.isNotEmpty()) loadFile(basePath)
            extraPaths.forEach { loadFile(it) }
            val loaded = basePath.isNotEmpty() && loadFile(localized(basePath, locale), optional = true)
            extraPaths.forEach { loadFile(localized(it, locale), optional = true) }
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
