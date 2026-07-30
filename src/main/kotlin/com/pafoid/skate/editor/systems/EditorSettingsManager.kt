package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.utils.UnitSystem
import java.io.File

class EditorSettingsManager(
    private val serializer: Serializer,
    private val eventSystem: EventSystem,
    private val logger: LoggerService,
    private val stringManager: StringManager
) {
    private val editorSettingsFile = File("editor.config")
    var editorSettings: EditorSettings = EditorSettings()

    val recentProjects: List<Project>
        get() {
            return editorSettings.recentProjects.mapNotNull { path ->
                val projectFile = File(path)
                serializer.decode<Project>(projectFile.readText()).takeIf { projectFile.exists() }
            }.take(5)
        }

    init {
        eventSystem.subscribe<ProjectEvent.Closed> { event -> setLastClosedProjectPath(event.project.projectPath) }
        eventSystem.subscribe<ProjectEvent.Opened> { event -> addToRecentProjects(event.project.projectPath) }
        eventSystem.subscribe<ProjectEvent.Created> { event -> addToRecentProjects(event.project.projectPath) }
    }

    fun loadSettings() {
        try {
            if (!editorSettingsFile.exists()) {
                logger.logEditor("Editor settings file was not found, creating a new one", LogLevel.WARN)
                editorSettingsFile.writeText(serializer.encode(EditorSettings()))
            }
            editorSettings = serializer.decode<EditorSettings>(editorSettingsFile.readText())
            logger.logEditor("Engine settings loaded")
        } catch (e: Exception) {
            logger.logEditor("Error loading engine settings: ${e.message}", LogLevel.ERROR)
            editorSettings = EditorSettings()
        }
    }

    fun saveSettings(): Boolean {
        return try {
            editorSettingsFile.writeText(serializer.encode(editorSettings))
            true
        } catch (e: Exception) {
            logger.logEditor("Error saving engine settings: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    fun setLastClosedProjectPath(path: String?) {
        editorSettings.lastClosedProjectPath = path
        saveSettings()
    }

    fun addToRecentProjects(projectPath: String) {
        editorSettings = editorSettings.addRecentProject(projectPath)
        saveSettings()
    }

    fun setLocale(locale: String) {
        editorSettings.language = locale
        stringManager.setLocale(locale)
        saveSettings()
    }

    fun updateEditorSettings(
        gamepadOverlaySize: Float? = null,
        showGamepadOverlay: Boolean? = null,
        unitSystem: UnitSystem? = null,
        language: String? = null,
        theme: String? = null,
        editorInputMappings: EditorInputMappings? = null
    ) {
        editorSettings = editorSettings.copy(
            gamepadOverlaySize = gamepadOverlaySize ?: editorSettings.gamepadOverlaySize,
            showGamepadOverlay = showGamepadOverlay ?: editorSettings.showGamepadOverlay,
            unitSystem = unitSystem ?: editorSettings.unitSystem,
            language = language ?: editorSettings.language,
            theme = theme ?: editorSettings.theme,
            editorInputMappings = editorInputMappings ?: editorSettings.editorInputMappings
        )
        language?.let { stringManager.setLocale(it) }
        saveSettings()
    }

    fun updateAutoSaveSettings(enabled: Boolean? = null, intervalMinutes: Int? = null) {
        editorSettings.autoSaveEnabled = enabled ?: editorSettings.autoSaveEnabled
        editorSettings.autorSaveIntervalMinutes = intervalMinutes ?: editorSettings.autorSaveIntervalMinutes
        saveSettings()
    }

    fun updateInputMappings(inputMappings: EditorInputMappings) {
        try {
            editorSettings = editorSettings.copy(editorInputMappings = inputMappings)
            saveSettings()
        } catch (e: Exception) {
            logger.logEditor("Error saving input mappings: ${e.message}", LogLevel.ERROR)
        }
    }
}
