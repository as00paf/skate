package com.pafoid.skate.engine.utils

import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

data class SystemSettings(
    var width: Int = 1920,
    var height: Int = 1080,
    var vsync: Boolean = true,
    var fullscreen: Boolean = false,
    var borderless: Boolean = false
)

object SettingsManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private const val SETTINGS_FILE = "settings.json"
    var settings = SystemSettings()
        private set

    fun load() {
        val path = Paths.get(SETTINGS_FILE)
        if (Files.exists(path)) {
            try {
                val json = String(Files.readAllBytes(path))
                settings = gson.fromJson(json, SystemSettings::class.java)
            } catch (e: Exception) {
                println("Error loading settings: ${e.message}")
            }
        }
    }

    fun save() {
        try {
            val writer = FileWriter(SETTINGS_FILE)
            writer.write(gson.toJson(settings))
            writer.close()
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
}
