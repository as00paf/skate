package com.pafoid.skate.engine.utils

import java.io.InputStream
import java.util.*

class StringManager(private var currentLocale: String = "en") {

    private val properties = Properties()

    init {
        loadStrings()
    }

    private fun loadStrings() {
        try {
            val resourcePath = "/values/strings_${currentLocale}.properties"
            val inputStream: InputStream? = StringManager::class.java.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                inputStream.use {
                    properties.load(it)
                }
            } else {
                // Fallback to default English if specific locale not found
                val defaultPath = "/values/strings.properties"
                val defaultInputStream: InputStream? = StringManager::class.java.getResourceAsStream(defaultPath)
                defaultInputStream?.use {
                    properties.load(it)
                } ?: run {
                    println("ERROR: Could not find default resource file: $defaultPath")
                }
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load strings for locale $currentLocale")
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

    fun getString(key: String): String {
        return properties.getProperty(key, "!!${key}!!")
    }

    fun getString(key: String, vararg formatArgs: Any): String {
        val formatString = properties.getProperty(key, "!!${key}!!")
        return String.format(formatString, *formatArgs)
    }

    fun getQuantityString(key: String, quantity: Int, vararg formatArgs: Any): String {
        val resourceKey = when (quantity) {
            1 -> "$key.one"
            else -> "$key.other"
        }
        val formatString = properties.getProperty(resourceKey, "!!${resourceKey}!!")
        // Prepend the quantity to the format arguments for keys like ".other"
        return String.format(formatString, quantity, *formatArgs)
    }
}
