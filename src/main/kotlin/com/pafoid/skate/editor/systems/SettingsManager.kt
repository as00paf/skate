package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.SystemSettings
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.serialization.Serializer
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

class SettingsManager(private val serializer: Serializer, private val logger: LoggerService, private val stringManager: StringManager) {
    var settings = SystemSettings()
        private set

    fun load() {
        val path = Paths.get(Assets.Files.SETTINGS_FILE)
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
            val writer = FileWriter(Assets.Files.SETTINGS_FILE)
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