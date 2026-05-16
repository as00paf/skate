package com.pafoid.skate.game.trick

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.InputStream
import java.util.*

class TrickManager(private val resourcePath: String = "/values/tricks.properties") : KoinComponent {
    private val logger: LoggerService by inject()

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
                logger.logEngine("Could not find resource file: $resourcePath", LogLevel.ERROR)
            }
        } catch (e: Exception) {
            logger.logEngine("Failed to load tricks from $resourcePath", LogLevel.ERROR)
            e.printStackTrace()
        }
    }

    fun getTrickName(key: String): String {
        return properties.getProperty(key, key) // Fallback to the key itself if not found
    }
}