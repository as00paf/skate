package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.settings.EngineSettings
import com.pafoid.skate.engine.settings.ProjectSettings
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

class SettingsManager(private val serializer: Serializer, private val logger: LoggerService, private val stringManager: StringManager) {
    var engine = EngineSettings()
        private set
    var project = ProjectSettings()
        private set

    fun load() {
        loadEngine()
        loadProject()
    }

    fun loadEngine() {
        val path = Paths.get(Assets.Files.ENGINE_SETTINGS_FILE)
        if (Files.exists(path)) {
            try {
                val json = String(Files.readAllBytes(path))
                engine = serializer.decode(json)
                stringManager.setLocale(engine.editor.language)
            } catch (e: Exception) {
                logger.logEngine("Error loading engine settings: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun loadProject() {
        val path = Paths.get(Assets.Files.PROJECT_SETTINGS_FILE)
        if (Files.exists(path)) {
            try {
                val json = String(Files.readAllBytes(path))
                project = serializer.decode(json)
            } catch (e: Exception) {
                logger.logEngine("Error loading project settings: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun save() {
        saveEngine()
        saveProject()
    }

    fun saveEngine() {
        try {
            val writer = FileWriter(Assets.Files.ENGINE_SETTINGS_FILE)
            writer.write(serializer.encode(engine))
            writer.close()
        } catch (e: Exception) {
            logger.logEngine("Error saving engine settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun saveProject() {
        try {
            val writer = FileWriter(Assets.Files.PROJECT_SETTINGS_FILE)
            writer.write(serializer.encode(project))
            writer.close()
        } catch (e: Exception) {
            logger.logEngine("Error saving project settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun setLocale(locale: String) {
        engine.editor.language = locale
        stringManager.setLocale(locale)
        saveEngine()
    }
}
