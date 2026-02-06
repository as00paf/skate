package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.assets.Assets.Files.SETTINGS_FILE
import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.utils.serialization.Serializer
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

class SettingsManager(private val serializer: Serializer, private val logger: LoggerService, private val stringManager: StringManager) {
    var settings = SystemSettings()
        private set

    fun load() {
        val path = Paths.get(SETTINGS_FILE)
        if (Files.exists(path)) {
            try {
                val json = String(Files.readAllBytes(path))
                settings = serializer.decode(json)
                stringManager.setLocale(settings.language)
            } catch (e: Exception) {
                logger.logEngine("Error loading settings: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun save() {
        try {
            val writer = FileWriter(SETTINGS_FILE)
            writer.write(serializer.encode(settings))
            writer.close()
        } catch (e: Exception) {
            logger.logEngine("Error saving settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun setLocale(locale: String) {
        settings.language = locale
        stringManager.setLocale(locale)
        save()
    }
}
