package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.settings.HardwareSettings
import com.pafoid.skate.editor.settings.RecentProjectInfo
import com.pafoid.skate.editor.settings.SettingsData
import com.pafoid.skate.editor.settings.SettingsSerializer
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import java.io.File

class SettingsManager(
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val stringManager: StringManager
) {
    private val settingsSerializer: SettingsSerializer = SettingsSerializer(serializer)

    var editor: EditorSettings = EditorSettings()

    // TODO: this is not used
    var hardware: HardwareSettings = HardwareSettings()

    val recentProjects: List<RecentProjectInfo>
        get() {
            return editor.recentProjects.mapNotNull { path ->
                val projectFile = File(path)
                if (projectFile.exists()) {
                    val projectSettings = settingsSerializer.loadProjectSettings(projectFile)
                    projectSettings?.let { RecentProjectInfo.fromProjectSettings(it) }
                } else {
                    null
                }
            }.take(5)
        }

    fun load() {
        loadEngine()
        loadHardware()
    }

    fun loadEngine() {
        try {
            editor = settingsSerializer.loadEngineSettings()
            stringManager.setLocale(editor.language)
            logger.logEditor("Engine settings loaded")
        } catch (e: Exception) {
            logger.logEditor("Error loading engine settings: ${e.message}", LogLevel.ERROR)
            editor = EditorSettings()
        }
    }

    fun loadHardware() {
        try {
            hardware = HardwareSettings()
            logger.logEditor("Hardware settings loaded (defaults)")
        } catch (e: Exception) {
            logger.logEditor("Error loading hardware settings: ${e.message}", LogLevel.ERROR)
            hardware = HardwareSettings()
        }
    }

    fun save() {
        saveEditorSettings()
        saveHardware()
    }

    fun saveEditorSettings() {
        try {
            settingsSerializer.saveEngineSettings(editor)
            logger.logEditor("Engine settings saved")
        } catch (e: Exception) {
            logger.logEditor("Error saving engine settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun saveHardware(hw: HardwareSettings? = null) {
        try {
            hw?.let { hardware = it }
            // Hardware save is deferred until persistence layer is implemented
            logger.logEditor("Hardware settings updated (persistence deferred)")
        } catch (e: Exception) {
            logger.logEditor("Error saving hardware settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun saveProject(project: Project): Boolean {
        return try {
            val result = settingsSerializer.saveProjectSettings(project)
            if (result) {
                logger.logEditor("Project saved: ${project.name}")
            } else {
                logger.logEditor("Failed to save project: ${project.name}", LogLevel.ERROR)
            }
            result
        } catch (e: Exception) {
            logger.logEditor("Error saving project: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    fun closeProject() {
        logger.logEditor("Project closed")
    }

    fun setLastClosedProjectPath(path: String?) {
        editor.lastClosedProjectPath = path
        saveEditorSettings()
    }

    fun addToRecentProjects(projectPath: String) {
        editor = editor.addRecentProject(projectPath)
        saveEditorSettings()
    }

    fun setLocale(locale: String) {
        editor.language = locale
        stringManager.setLocale(locale)
        saveEditorSettings()
    }

    fun updateEditorSettings(
        gamepadOverlaySize: Float? = null,
        showGamepadOverlay: Boolean? = null,
        unitSystem: UnitSystem? = null,
        language: String? = null,
        theme: String? = null,
        editorInputMappings: EditorInputMappings? = null
    ) {
        editor = editor.copy(
            gamepadOverlaySize = gamepadOverlaySize ?: editor.gamepadOverlaySize,
            showGamepadOverlay = showGamepadOverlay ?: editor.showGamepadOverlay,
            unitSystem = unitSystem ?: editor.unitSystem,
            language = language ?: editor.language,
            theme = theme ?: editor.theme,
            editorInputMappings = editorInputMappings ?: editor.editorInputMappings
        )
        language?.let { stringManager.setLocale(it) }
        saveEditorSettings()
    }

    fun updateAutoSaveSettings(enabled: Boolean? = null, intervalMinutes: Int? = null) {
        editor.autoSaveEnabled = enabled ?: editor.autoSaveEnabled
        editor.autorSaveIntervalMinutes = intervalMinutes ?: editor.autorSaveIntervalMinutes
        saveEditorSettings()
    }

    fun updateInputMappings(inputMappings: EditorInputMappings) {
        // Store input mappings in a dedicated file for now
        try {
            val file = SettingsData.getSettingsDirectory().resolve("input_mappings.json")// TODO: const
            file.parentFile?.mkdirs()
            file.writeText(serializer.encode(inputMappings))
            logger.logEditor("Input mappings saved to ${file.absolutePath}")
        } catch (e: Exception) {
            logger.logEditor("Error saving input mappings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun loadInputMappings(): InputMappings? {
        return try {
            val file = SettingsData.getSettingsDirectory().resolve("input_mappings.json")// TODO: const
            if (file.exists()) {
                serializer.decode<InputMappings>(file.readText())
            } else null
        } catch (e: Exception) {
            logger.logEditor("Using default input mappings")
            null
        }
    }
}
