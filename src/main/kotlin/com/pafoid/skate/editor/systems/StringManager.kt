package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.engine.contracts.IStringManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.InputStream
import java.util.*

class StringManager(
    private val baseName: String = "strings",
    private var currentLocale: String = "en"
) : KoinComponent, IStringManager {
    private val logger: LoggerService by inject()

    private val properties = Properties()

    init {
        loadStrings()
    }

    private fun loadStrings() {
        try {
            val resourcePath = "/values/${baseName}_${currentLocale}.properties"
            val inputStream: InputStream? = StringManager::class.java.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                inputStream.use {
                    properties.load(it)
                }
            } else {
                // Fallback to default if specific locale not found
                val defaultPath = "/values/${baseName}.properties"
                val defaultInputStream: InputStream? = StringManager::class.java.getResourceAsStream(defaultPath)
                defaultInputStream?.use {
                    properties.load(it)
                } ?: run {
                    logger.logEngine("Could not find default resource file: $defaultPath", LogLevel.ERROR)
                }
            }
        } catch (e: Exception) {
            logger.logEngine("Failed to load strings for baseName $baseName and locale $currentLocale", LogLevel.ERROR)
            e.printStackTrace()
        }
    }

    fun setLocale(locale: String) {
        if (currentLocale != locale) {
            currentLocale = locale
            properties.clear() // Clear existing properties before reloading
            loadStrings()
        }
    }

    override fun getString(key: String): String {
        val value = properties.getProperty(key)
        if (value == null) {
            logger.logEditor("Missing string key: $key")
        }
        return value ?: "!!${key}!!"
    }

    override fun getString(key: String, vararg formatArgs: Any): String {
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
