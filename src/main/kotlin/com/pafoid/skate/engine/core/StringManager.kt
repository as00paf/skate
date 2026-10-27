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

    private fun loadFile(path: String): Boolean {
        currentPath = path
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
            val loaded = loadFile(localized(basePath, locale))
            extraPaths.forEach { loadFile(localized(it, locale)) }
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

    fun getQuantityString(key: String, quantity: Int, vararg formatArgs: Any): String {
        val resourceKey = when (quantity) {
            1 -> "$key.one"
            else -> "$key.other"
        }
        val formatString = properties.getProperty(resourceKey, "!!${resourceKey}!!")
        return String.format(formatString, quantity, *formatArgs)
    }
}