package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.settings.EngineSettings
import com.pafoid.skate.engine.settings.HardwareSettings
import com.pafoid.skate.engine.settings.ProjectSettings
import com.pafoid.skate.engine.settings.RecentProjectInfo
import com.pafoid.skate.engine.settings.SettingsData
import com.pafoid.skate.engine.settings.SettingsSerializer
import com.pafoid.skate.engine.settings.UserSettings
import java.io.File

/**
 * Centralized manager for all settings types.
 * Handles loading, saving, and validation of engine, user, hardware, and project settings.
 *
 * @param serializer JSON serializer for settings persistence
 * @param logger Logger for error reporting
 * @param stringManager String manager for localization
 */
class SettingsManager(
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val stringManager: StringManager
) {
    private val settingsSerializer: SettingsSerializer = SettingsSerializer(serializer)
    
    /**
     * Engine-wide settings (global, engine-wide configuration).
     */
    var engine: EngineSettings = EngineSettings()
        private set
    
    /**
     * User-specific preferences (persist across projects).
     */
    var user: UserSettings = UserSettings()
        private set
    
    /**
     * Hardware settings (display, graphics, audio).
     */
    var hardware: HardwareSettings = HardwareSettings()
        private set
    
    /**
     * Current project settings (null if no project loaded).
     */
    var project: ProjectSettings? = null
        private set
    
    /**
     * List of recent projects (from user settings).
     */
    val recentProjects: List<RecentProjectInfo>
        get() = user.recentProjects.mapNotNull { path ->
            val projectFile = File(path)
            if (projectFile.exists()) {
                val projectSettings = settingsSerializer.loadProjectSettings(projectFile)
                projectSettings?.let { RecentProjectInfo.fromProjectSettings(it) }
            } else {
                null
            }
        }.take(5)
    
    /**
     * Load all settings on startup.
     */
    fun load() {
        loadEngine()
        loadUser()
        loadHardware()
    }
    
    /**
     * Load engine settings from file.
     */
    fun loadEngine() {
        try {
            engine = settingsSerializer.loadEngineSettings().validate()
            stringManager.setLocale(engine.editor.language)
            logger.logEditor("Engine settings loaded")
        } catch (e: Exception) {
            logger.logEngine("Error loading engine settings: ${e.message}", LogLevel.ERROR)
            engine = EngineSettings()
        }
    }
    
    /**
     * Load user settings from file.
     */
    fun loadUser() {
        try {
            user = settingsSerializer.loadUserSettings().validate()
            logger.logEditor("User settings loaded")
        } catch (e: Exception) {
            logger.logEngine("Error loading user settings: ${e.message}", LogLevel.ERROR)
            user = UserSettings()
        }
    }
    
    /**
     * Load hardware settings from file.
     */
    fun loadHardware() {
        try {
            // For now, use default hardware settings
            // Future: load from file
            hardware = HardwareSettings().validate()
            logger.logEditor("Hardware settings loaded (defaults)")
        } catch (e: Exception) {
            logger.logEngine("Error loading hardware settings: ${e.message}", LogLevel.ERROR)
            hardware = HardwareSettings()
        }
    }
    
    /**
     * Save all settings.
     */
    fun save() {
        saveEngine()
        saveUser()
        saveHardware()
        project?.let { saveProject(it) }
    }
    
    /**
     * Save engine settings to file.
     */
    fun saveEngine() {
        try {
            settingsSerializer.saveEngineSettings(engine)
            logger.logEditor("Engine settings saved")
        } catch (e: Exception) {
            logger.logEngine("Error saving engine settings: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Save user settings to file.
     */
    fun saveUser() {
        try {
            settingsSerializer.saveUserSettings(user)
            logger.logEditor("User settings saved")
        } catch (e: Exception) {
            logger.logEngine("Error saving user settings: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Save hardware settings to file.
     */
    fun saveHardware() {
        try {
            // For now, hardware settings are not persisted
            // Future: save to file
            logger.logEditor("Hardware settings saved (defaults)")
        } catch (e: Exception) {
            logger.logEngine("Error saving hardware settings: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Load project settings from .skateproject file.
     */
    fun loadProject(projectFile: File): Boolean {
        return try {
            val loadedProject = settingsSerializer.loadProjectSettings(projectFile)
            if (loadedProject != null) {
                project = loadedProject
                // Update last opened date
                val updatedMetadata = loadedProject.metadata.copy(
                    lastOpenedDate = System.currentTimeMillis()
                )
                val updatedProject = loadedProject.copy(metadata = updatedMetadata)
                saveProject(updatedProject)
                
                // Add to recent projects
                addToRecentProjects(projectFile.absolutePath)
                
                logger.logEditor("Project loaded: ${loadedProject.metadata.name}")
                true
            } else {
                logger.logEngine("Failed to load project: $projectFile", LogLevel.ERROR)
                false
            }
        } catch (e: Exception) {
            logger.logEngine("Error loading project: ${e.message}", LogLevel.ERROR)
            false
        }
    }
    
    /**
     * Save project settings to .skateproject file.
     */
    fun saveProject(projectSettings: ProjectSettings) {
        try {
            settingsSerializer.saveProjectSettings(projectSettings)
            logger.logEditor("Project saved: ${projectSettings.metadata.name}")
        } catch (e: Exception) {
            logger.logEngine("Error saving project: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Create a new project.
     */
    fun createProject(name: String, folder: File, engineVersion: String): Result<ProjectSettings> {
        return settingsSerializer.createProject(name, folder, engineVersion)
            .onSuccess { project = it }
            .onSuccess { addToRecentProjects(it.getProjectFile().absolutePath) }
    }
    
    /**
     * Close the current project.
     */
    fun closeProject() {
        project = null
        logger.logEditor("Project closed")
    }
    
    /**
     * Add a project to recent projects list.
     */
    fun addToRecentProjects(projectPath: String) {
        user = user.addRecentProject(projectPath)
        saveUser()
    }
    
    /**
     * Set the UI locale and save.
     */
    fun setLocale(locale: String) {
        engine = engine.copy(
            editor = engine.editor.copy(language = locale)
        )
        stringManager.setLocale(locale)
        saveEngine()
    }
    
    /**
     * Check if a project is currently loaded.
     */
    fun hasProject(): Boolean = project != null
    
    /**
     * Get the current project name or "No Project" if none loaded.
     */
    fun getProjectName(): String = project?.metadata?.name ?: "No Project"
}
