package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.settings.EngineSettings
import com.pafoid.skate.editor.settings.HardwareSettings
import com.pafoid.skate.editor.settings.RecentProjectInfo
import com.pafoid.skate.editor.settings.SettingsData
import com.pafoid.skate.editor.settings.SettingsSerializer
import com.pafoid.skate.editor.settings.UserSettings
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.input.InputMappings
import java.io.File

class SettingsManager(
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val stringManager: StringManager
) {
    private val settingsSerializer: SettingsSerializer = SettingsSerializer(serializer)

    var engine: EngineSettings = EngineSettings()
        private set

    var user: UserSettings = UserSettings()
        private set

    var hardware: HardwareSettings = HardwareSettings()
        private set

    private var cachedRecentProjects: List<RecentProjectInfo>? = null

    val recentProjects: List<RecentProjectInfo>
        get() {
            if (cachedRecentProjects == null) {
                cachedRecentProjects = user.recentProjects.mapNotNull { path ->
                    val projectFile = File(path)
                    if (projectFile.exists()) {
                        val projectSettings = settingsSerializer.loadProjectSettings(projectFile)
                        projectSettings?.let { RecentProjectInfo.fromProjectSettings(it) }
                    } else {
                        null
                    }
                }.take(5)
            }
            return cachedRecentProjects ?: emptyList()
        }

    fun load() {
        loadEngine()
        loadUser()
        loadHardware()
    }

    fun loadEngine() {
        try {
            engine = settingsSerializer.loadEngineSettings().validate()
            stringManager.setLocale(engine.editor.language)
            logger.logEditor("Engine settings loaded")
        } catch (e: Exception) {
            logger.logEditor("Error loading engine settings: ${e.message}", LogLevel.ERROR)
            engine = EngineSettings()
        }
    }

    fun loadUser() {
        try {
            user = settingsSerializer.loadUserSettings().validate()
            logger.logEditor("User settings loaded")
        } catch (e: Exception) {
            logger.logEditor("Error loading user settings: ${e.message}", LogLevel.ERROR)
            user = UserSettings()
        }
    }

    fun loadHardware() {
        try {
            hardware = HardwareSettings().validate()
            logger.logEditor("Hardware settings loaded (defaults)")
        } catch (e: Exception) {
            logger.logEditor("Error loading hardware settings: ${e.message}", LogLevel.ERROR)
            hardware = HardwareSettings()
        }
    }

    fun save() {
        saveEngine()
        saveUser()
        saveHardware()
    }

    fun saveEngine() {
        try {
            settingsSerializer.saveEngineSettings(engine)
            logger.logEditor("Engine settings saved")
        } catch (e: Exception) {
            logger.logEditor("Error saving engine settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun saveUser() {
        try {
            settingsSerializer.saveUserSettings(user)
            logger.logEditor("User settings saved")
        } catch (e: Exception) {
            logger.logEditor("Error saving user settings: ${e.message}", LogLevel.ERROR)
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

    fun loadProject(projectFile: File): Project? {
        return try {
            val loadedProject = settingsSerializer.loadProjectSettings(projectFile)
            if (loadedProject != null) {
                val updatedMetadata = loadedProject.metadata.copy(
                    lastOpenedDate = System.currentTimeMillis()
                )
                val updatedProject = loadedProject.copy(metadata = updatedMetadata)
                saveProject(updatedProject)
                addToRecentProjects(projectFile.absolutePath)
                logger.logEditor("Project loaded: ${loadedProject.metadata.name}")
                updatedProject
            } else {
                logger.logEditor("Failed to load project: $projectFile", LogLevel.ERROR)
                null
            }
        } catch (e: Exception) {
            logger.logEditor("Error loading project: ${e.message}", LogLevel.ERROR)
            null
        }
    }

    fun saveProject(project: Project): Boolean {
        return try {
            val result = settingsSerializer.saveProjectSettings(project)
            if (result) {
                logger.logEditor("Project saved: ${project.metadata.name}")
            } else {
                logger.logEditor("Failed to save project: ${project.metadata.name}", LogLevel.ERROR)
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
        user = user.copy(lastClosedProjectPath = path)
        saveUser()
    }

    fun getLastClosedProjectPath(): String? {
        return user.lastClosedProjectPath
    }

    fun addToRecentProjects(projectPath: String) {
        cachedRecentProjects = null
        user = user.addRecentProject(projectPath)
        saveUser()
    }

    fun setLocale(locale: String) {
        engine = engine.copy(
            editor = engine.editor.copy(language = locale)
        )
        stringManager.setLocale(locale)
        saveEngine()
    }

    fun updateEditorSettings(
        gamepadOverlaySize: Float? = null,
        showGamepadOverlay: Boolean? = null,
        unitSystem: com.pafoid.skate.engine.utils.UnitSystem? = null,
        language: String? = null,
        theme: String? = null,
        editorInputMappings: EditorInputMappings? = null
    ) {
        val currentEditor = engine.editor
        engine = engine.copy(
            editor = currentEditor.copy(
                gamepadOverlaySize = gamepadOverlaySize ?: currentEditor.gamepadOverlaySize,
                showGamepadOverlay = showGamepadOverlay ?: currentEditor.showGamepadOverlay,
                unitSystem = unitSystem ?: currentEditor.unitSystem,
                language = language ?: currentEditor.language,
                theme = theme ?: currentEditor.theme,
                editorInputMappings = editorInputMappings ?: currentEditor.editorInputMappings
            )
        )
        language?.let { stringManager.setLocale(it) }
        saveEngine()
    }

    fun updateAutoSaveSettings(enabled: Boolean? = null, intervalMinutes: Int? = null) {
        val current = engine.autoSave
        engine = engine.copy(
            autoSave = current.copy(
                enabled = enabled ?: current.enabled,
                intervalMinutes = intervalMinutes ?: current.intervalMinutes
            )
        )
        saveEngine()
    }

    fun updateInputMappings(inputMappings: InputMappings) {
        // Store input mappings in a dedicated file for now
        // TODO Phase 5: Integrate with proper settings persistence
        try {
            val file = SettingsData.getSettingsDirectory().resolve("input_mappings.json")
            file.parentFile?.mkdirs()
            file.writeText(serializer.encode(inputMappings))
            logger.logEditor("Input mappings saved to ${file.absolutePath}")
        } catch (e: Exception) {
            logger.logEditor("Error saving input mappings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun loadInputMappings(): InputMappings? {
        return try {
            val file = SettingsData.getSettingsDirectory().resolve("input_mappings.json")
            if (file.exists()) {
                serializer.decode<InputMappings>(file.readText())
            } else null
        } catch (e: Exception) {
            logger.logEditor("Using default input mappings")
            null
        }
    }

    fun getCurrentHardware() = hardware
}
