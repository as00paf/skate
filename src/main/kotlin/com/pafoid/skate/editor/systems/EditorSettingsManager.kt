package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectFileReader
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.settings.ShadowQuality
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
    private val stringManager: StringManager,
    private val editorSettingsFile: File = EditorPaths.file(EditorPaths.SETTINGS_FILE),
) {
    var editorSettings: EditorSettings = EditorSettings()

    /** Called when MSAA samples change, allowing the editor to recreate the scene frame buffer. */
    var onMsaaChanged: ((samples: Int) -> Unit)? = null

    /**
     * Recent projects that still exist, newest first. The absolute `.skateproject` path kept in the settings is the
     * source of truth for [Project.projectPath] (EDT-41): the file itself stores it project-relative (AST-09).
     */
    val recentProjects: List<Project>
        get() = editorSettings.recentProjects.mapNotNull { path -> ProjectFileReader.readOrNull(serializer, File(path)) }.take(5)

    /** The most recent project file that still exists, as stored in the settings, or null (EDT-41). */
    fun lastProjectFile(): File? = editorSettings.recentProjects.asSequence().map(::File).firstOrNull { it.isFile }

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
        editorSettings.graphics.applyToEngine()
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

    fun updateCompletionSettings(autoPopup: Boolean, delayMs: Int) {
        editorSettings.completionAutoPopup = autoPopup
        editorSettings.completionDelayMs = delayMs.coerceIn(0, 2000)
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

    /** Stores [quality], applies it to the renderer right away and saves it to the settings file. */
    fun updateShadowQuality(quality: ShadowQuality) {
        editorSettings = editorSettings.copy(graphics = editorSettings.graphics.copy(shadowQuality = quality))
        editorSettings.graphics.applyToEngine()
        saveSettings()
    }

    /** Stores [msaa], applies it to the renderer right away and saves it to the settings file. */
    fun updateMsaa(samples: Int) {
        editorSettings = editorSettings.copy(graphics = editorSettings.graphics.copy(msaa = samples))
        editorSettings.graphics.applyToEngine()
        onMsaaChanged?.invoke(samples)
        saveSettings()
    }

    /** Stores [textureQuality], applies it to the renderer right away and saves it to the settings file. */
    fun updateTextureQuality(textureQuality: com.pafoid.skate.editor.settings.TextureQuality) {
        editorSettings = editorSettings.copy(graphics = editorSettings.graphics.copy(textureQuality = textureQuality))
        editorSettings.graphics.applyToEngine()
        saveSettings()
    }

    /** Stores the overall [quality] preset, applies all derived settings and saves. */
    fun updateGraphicsQuality(preset: com.pafoid.skate.editor.settings.GraphicsQuality) {
        editorSettings = editorSettings.copy(graphics = editorSettings.graphics.copy(quality = preset))
        editorSettings.graphics.applyToEngine()
        saveSettings()
    }

    /** Stores the export's game jar override ([EditorSettings.gameJarPath]); blank clears it. */
    fun updateGameJarPath(path: String?) {
        editorSettings = editorSettings.copy(gameJarPath = path?.trim()?.ifEmpty { null })
        saveSettings()
    }
}
