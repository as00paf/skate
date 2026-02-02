package com.pafoid.skate.engine.utils

import java.io.InputStream
import java.util.*

class TrickManager(private val resourcePath: String = "/values/tricks.properties") {

    private val properties = Properties()

    init {
        loadTricks()
    }

    private fun loadTricks() {
        try {
            val inputStream: InputStream? = TrickManager::class.java.getResourceAsStream(resourcePath)
            inputStream?.use {
                properties.load(it)
            } ?: run {
                println("ERROR: Could not find resource file: $resourcePath")
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load tricks from $resourcePath")
            e.printStackTrace()
        }
    }

    fun getTrickName(key: String): String {
        return properties.getProperty(key, key) // Fallback to the key itself if not found
    }
}
