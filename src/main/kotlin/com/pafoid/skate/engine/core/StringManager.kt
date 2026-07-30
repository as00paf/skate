package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.core.LoggerService.LogLevel
import java.io.File
import java.util.*

class StringManager(
    private val logger: LoggerService,
    private var currentLocale: String = "en"
) {
    private var currentPath = ""
    private val properties = Properties()

    fun loadStrings(path: String): Boolean {
        currentPath = path
        try {
            val inputFile = File(path)
            inputFile.inputStream().use {
                properties.load(it)
            }
        } catch (e: Exception) {
            logger.logEditor("Failed to load strings for baseName $path and locale $currentLocale", LogLevel.ERROR)
            e.printStackTrace()
            return false
        }
        return true
    }

    fun setLocale(locale: String): Boolean {
        if (currentLocale != locale) {
            currentLocale = locale
            properties.clear() // Clear existing properties before reloading
            val currentFile = File(currentPath)
            val currentExt = currentFile.extension
            val newFileName = currentFile.absolutePath.replace(".$currentExt", "_$locale.$currentExt")
            return loadStrings(newFileName)
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