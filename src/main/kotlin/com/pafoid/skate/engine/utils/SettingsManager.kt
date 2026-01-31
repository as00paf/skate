package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.utils.serialization.Serializer
import kotlinx.serialization.encodeToString
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

object SettingsManager {
    private const val SETTINGS_FILE = "settings.json"
    var settings = SystemSettings()
        private set

    fun load() {
        val path = Paths.get(SETTINGS_FILE)
        if (Files.exists(path)) {
            try {
                val json = String(Files.readAllBytes(path))
                settings = Serializer.json.decodeFromString(json)
            } catch (e: Exception) {
                println("Error loading settings: ${e.message}")
            }
        }
    }

    fun save() {
        try {
            val writer = FileWriter(SETTINGS_FILE)
            writer.write(Serializer.json.encodeToString(settings))
            writer.close()
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
}
