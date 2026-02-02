package com.pafoid.skate.engine.utils

import java.io.InputStream
import java.util.*

class StringManager(private val resourcePath: String = "/values/strings.properties") {

    private val properties = Properties()

    init {
        loadStrings()
    }

    private fun loadStrings() {
        try {
            val inputStream: InputStream? = StringManager::class.java.getResourceAsStream(resourcePath)
            inputStream?.use {
                properties.load(it)
            } ?: run {
                println("ERROR: Could not find resource file: $resourcePath")
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load strings from $resourcePath")
            e.printStackTrace()
        }
    }

    fun getString(key: String): String {
        return properties.getProperty(key, "!!${key}!!")
    }

    fun getString(key: String, vararg formatArgs: Any): String {
        val formatString = properties.getProperty(key, "!!${key}!!")
        return String.format(formatString, *formatArgs)
    }
}
