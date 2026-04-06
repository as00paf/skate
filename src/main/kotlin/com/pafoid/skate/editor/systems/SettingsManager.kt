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

    var project: ProjectSettings? = null
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
            logger.logEngine("Error loading engine settings: ${e.message}", LogLevel.ERROR)
            engine = EngineSettings()
        }
    }

    fun loadUser() {
        try {
            user = settingsSerializer.loadUserSettings().validate()
            logger.logEditor("User settings loaded")
        } catch (e: Exception) {
            logger.logEngine("Error loading user settings: ${e.message}", LogLevel.ERROR)
            user = UserSettings()
        }
    }

    fun loadHardware() {
        try {
            hardware = HardwareSettings().validate()
            logger.logEditor("Hardware settings loaded (defaults)")
        } catch (e: Exception) {
            logger.logEngine("Error loading hardware settings: ${e.message}", LogLevel.ERROR)
            hardware = HardwareSettings()
        }
    }

    fun save() {
        saveEngine()
        saveUser()
        saveHardware()
        project?.let { saveProject(it) }
    }

    fun saveEngine() {
        try {
            settingsSerializer.saveEngineSettings(engine)
            logger.logEditor("Engine settings saved")
        } catch (e: Exception) {
            logger.logEngine("Error saving engine settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun saveUser() {
        try {
            settingsSerializer.saveUserSettings(user)
            logger.logEditor("User settings saved")
        } catch (e: Exception) {
            logger.logEngine("Error saving user settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun saveHardware(hw: HardwareSettings? = null) {
        try {
            hw?.let { hardware = it }
            // Hardware save is deferred until persistence layer is implemented
            logger.logEditor("Hardware settings updated (persistence deferred)")
        } catch (e: Exception) {
            logger.logEngine("Error saving hardware settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun loadProject(projectFile: File): Boolean {
        return try {
            val loadedProject = settingsSerializer.loadProjectSettings(projectFile)
            if (loadedProject != null) {
                project = loadedProject
                val updatedMetadata = loadedProject.metadata.copy(
                    lastOpenedDate = System.currentTimeMillis()
                )
                val updatedProject = loadedProject.copy(metadata = updatedMetadata)
                saveProject(updatedProject)

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

    fun saveProject(projectSettings: ProjectSettings): Boolean {
        return try {
            val result = settingsSerializer.saveProjectSettings(projectSettings)
            if (result) {
                logger.logEditor("Project saved: ${projectSettings.metadata.name}")
            } else {
                logger.logEngine("Failed to save project: ${projectSettings.metadata.name}", LogLevel.ERROR)
            }
            result
        } catch (e: Exception) {
            logger.logEngine("Error saving project: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    fun createProject(name: String, folder: File, engineVersion: String): Result<ProjectSettings> {
        return settingsSerializer.createProject(name, folder, engineVersion)
            .onSuccess { project = it }
            .onSuccess { addToRecentProjects(it.getProjectFile().absolutePath) }
    }

    fun closeProject() {
        project = null
        logger.logEditor("Project closed")
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
        theme: String? = null
    ) {
        val currentEditor = engine.editor
        engine = engine.copy(
            editor = currentEditor.copy(
                gamepadOverlaySize = gamepadOverlaySize ?: currentEditor.gamepadOverlaySize,
                showGamepadOverlay = showGamepadOverlay ?: currentEditor.showGamepadOverlay,
                unitSystem = unitSystem ?: currentEditor.unitSystem,
                language = language ?: currentEditor.language,
                theme = theme ?: currentEditor.theme
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

    fun hasProject(): Boolean = project != null

    fun getProjectName(): String = project?.metadata?.name ?: "No Project"

    // ─── Display callbacks (set at app init, called from settings windows) ───
    private var vsyncCallback: ((Boolean) -> Unit)? = null

    fun setDisplayCallbacks(vsync: (Boolean) -> Unit, fullscreen: (Boolean) -> Unit) {
        vsyncCallback = vsync
        // fullscreen callback kept for future use
    }

    fun applyVSync(enabled: Boolean) {
        hardware = hardware.copy(display = hardware.display.copy(vsync = enabled))
        vsyncCallback?.invoke(enabled)
    }

    fun getCurrentHardware() = hardware
}
