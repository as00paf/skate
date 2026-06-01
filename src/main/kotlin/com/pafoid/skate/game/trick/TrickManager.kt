package com.pafoid.skate.game.trick

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.logGame
import com.pafoid.skate.engine.data.LogLevel
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
                logger.logGame("Could not find resource file: $resourcePath", LogLevel.ERROR)
            }
        } catch (e: Exception) {
            logger.logGame("Failed to load tricks from $resourcePath", LogLevel.ERROR)
            e.printStackTrace()
        }
    }

    fun getTrickName(key: String): String {
        return properties.getProperty(key, key) // Fallback to the key itself if not found
    }
}