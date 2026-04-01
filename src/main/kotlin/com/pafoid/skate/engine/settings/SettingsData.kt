package com.pafoid.skate.engine.settings

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Helper functions and data classes for settings serialization.
 */
object SettingsData {
    const val ENGINE_SETTINGS_FILE = "engine_settings.json"
    const val USER_SETTINGS_FILE = "user_settings.json"
    const val PROJECT_FILE_EXTENSION = ".skateproject"
    
    /**
     * Get the default engine settings directory.
     */
    fun getSettingsDirectory(): File {
        val userHome = System.getProperty("user.home")
        val settingsDir = File(userHome, ".skateSim/settings")
        if (!settingsDir.exists()) {
            settingsDir.mkdirs()
        }
        return settingsDir
    }
    
    /**
     * Get the path to the engine settings file.
     */
    fun getEngineSettingsFile(): File {
        return File(getSettingsDirectory(), ENGINE_SETTINGS_FILE)
    }
    
    /**
     * Get the path to the user settings file.
     */
    fun getUserSettingsFile(): File {
        return File(getSettingsDirectory(), USER_SETTINGS_FILE)
    }
}

/**
 * Project metadata stored in .skateproject file.
 */
@Serializable
data class ProjectMetadata(
    val name: String,
    val version: String = "1.0.0",
    val engineVersion: String,
    val createdDate: Long = System.currentTimeMillis(),
    val lastOpenedDate: Long = System.currentTimeMillis(),
    val projectPath: String,
    val description: String = ""
)

/**
 * Project settings stored in .skateproject file.
 */
@Serializable
data class ProjectSettings(
    val metadata: ProjectMetadata,
    val defaultScene: String = "",
    val assetPaths: List<String> = listOf("Assets"),
    val scenePaths: List<String> = listOf("Scenes"),
    val buildPaths: List<String> = listOf("Builds"),
    val gameplaySettings: GameplaySettings = GameplaySettings()
) {
    /**
     * Get the project directory (parent of .skateproject file).
     */
    fun getProjectDirectory(): File {
        return File(metadata.projectPath).parentFile
    }
    
    /**
     * Get the full path to the .skateproject file.
     */
    fun getProjectFile(): File {
        return File(metadata.projectPath)
    }
}

/**
 * Gameplay-specific settings for the project.
 */
@Serializable
data class GameplaySettings(
    val physicsFPS: Int = 60,
    val gravity: Float = -9.81f,
    val timeScale: Float = 1.0f
)

/**
 * Recent project information for display in UI.
 */
@Serializable
data class RecentProjectInfo(
    val path: String,
    val name: String,
    val lastOpened: Long,
    val engineVersion: String
) {
    /**
     * Create from ProjectSettings.
     */
    companion object {
        fun fromProjectSettings(project: ProjectSettings): RecentProjectInfo {
            return RecentProjectInfo(
                path = project.metadata.projectPath,
                name = project.metadata.name,
                lastOpened = project.metadata.lastOpenedDate,
                engineVersion = project.metadata.engineVersion
            )
        }
    }
}

/**
 * Settings serialization helper.
 */
class SettingsSerializer(private val serializer: Serializer) {
    
    /**
     * Load engine settings from file.
     */
    fun loadEngineSettings(): EngineSettings {
        val file = SettingsData.getEngineSettingsFile()
        return if (file.exists()) {
            try {
                serializer.decode<EngineSettings>(file.readText())
            } catch (e: Exception) {
                EngineSettings() // Return defaults on error
            }
        } else {
            EngineSettings()
        }
    }
    
    /**
     * Save engine settings to file.
     */
    fun saveEngineSettings(settings: EngineSettings) {
        val file = SettingsData.getEngineSettingsFile()
        file.writeText(serializer.encode(settings))
    }
    
    /**
     * Load user settings from file.
     */
    fun loadUserSettings(): UserSettings {
        val file = SettingsData.getUserSettingsFile()
        return if (file.exists()) {
            try {
                serializer.decode<UserSettings>(file.readText())
            } catch (e: Exception) {
                UserSettings() // Return defaults on error
            }
        } else {
            UserSettings()
        }
    }
    
    /**
     * Save user settings to file.
     */
    fun saveUserSettings(settings: UserSettings) {
        val file = SettingsData.getUserSettingsFile()
        file.writeText(serializer.encode(settings))
    }
    
    /**
     * Load project settings from .skateproject file.
     */
    fun loadProjectSettings(projectFile: File): ProjectSettings? {
        return if (projectFile.exists() && projectFile.extension == "skateproject") {
            try {
                serializer.decode<ProjectSettings>(projectFile.readText())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Save project settings to .skateproject file.
     */
    fun saveProjectSettings(project: ProjectSettings): Boolean {
        return try {
            val file = project.getProjectFile()
            file.writeText(serializer.encode(project))
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Create a new project file with default settings.
     */
    fun createProject(
        name: String,
        folder: File,
        engineVersion: String
    ): Result<ProjectSettings> {
        return try {
            // Create project directory structure
            val projectDir = File(folder, name)
            if (!projectDir.exists()) {
                projectDir.mkdirs()
            }
            
            File(projectDir, "Assets").mkdirs()
            File(projectDir, "Scenes").mkdirs()
            File(projectDir, "Builds").mkdirs()
            
            // Create project metadata
            val metadata = ProjectMetadata(
                name = name,
                engineVersion = engineVersion,
                projectPath = File(projectDir, "$name.skateproject").absolutePath
            )
            
            // Create project settings
            val project = ProjectSettings(
                metadata = metadata,
                defaultScene = "",
                assetPaths = listOf("Assets"),
                scenePaths = listOf("Scenes"),
                buildPaths = listOf("Builds")
            )
            
            // Save project file
            val projectFile = File(projectDir, "$name.skateproject")
            saveProjectSettings(project)
            
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
