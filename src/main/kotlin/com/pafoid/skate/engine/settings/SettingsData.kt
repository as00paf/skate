package com.pafoid.skate.engine.settings

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetRegistryData
import com.pafoid.skate.engine.assets.serialization.Serializer
import kotlinx.serialization.Serializable
import java.io.File

object SettingsData {
    const val ENGINE_SETTINGS_FILE = "engine_settings.json"
    const val USER_SETTINGS_FILE = "user_settings.json"
    const val PROJECT_FILE_EXTENSION = ".skateproject"

    fun getSettingsDirectory(): File {
        val userHome = System.getProperty("user.home")
        val settingsDir = File(userHome, ".skateSim/settings")
        if (!settingsDir.exists()) {
            settingsDir.mkdirs()
        }
        return settingsDir
    }

    fun getEngineSettingsFile(): File {
        return File(getSettingsDirectory(), ENGINE_SETTINGS_FILE)
    }

    fun getUserSettingsFile(): File {
        return File(getSettingsDirectory(), USER_SETTINGS_FILE)
    }
}

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

@Serializable
data class ProjectSettings(
    val metadata: ProjectMetadata,
    val defaultScene: String = "",
    val assetPaths: List<String> = listOf("Assets"),
    val scenePaths: List<String> = listOf("Scenes"),
    val buildPaths: List<String> = listOf("Builds"),
    val gameplaySettings: GameplaySettings = GameplaySettings(),
    val assetRegistry: AssetRegistryData? = null
) {
    fun getProjectDirectory(): File {
        return File(metadata.projectPath).parentFile
    }

    fun getProjectFile(): File {
        return File(metadata.projectPath)
    }
}

@Serializable
data class GameplaySettings(
    val physicsFPS: Int = 60,
    val gravity: Float = -9.81f,
    val timeScale: Float = 1.0f
)

@Serializable
data class RecentProjectInfo(
    val path: String,
    val name: String,
    val lastOpened: Long,
    val engineVersion: String
) {
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

class SettingsSerializer(private val serializer: Serializer) {

    fun loadEngineSettings(): EngineSettings {
        val file = SettingsData.getEngineSettingsFile()
        return if (file.exists()) {
            try {
                serializer.decode<EngineSettings>(file.readText())
            } catch (e: Exception) {
                EngineSettings()
            }
        } else {
            EngineSettings()
        }
    }

    fun saveEngineSettings(settings: EngineSettings) {
        val file = SettingsData.getEngineSettingsFile()
        file.writeText(serializer.encode(settings))
    }

    fun loadUserSettings(): UserSettings {
        val file = SettingsData.getUserSettingsFile()
        return if (file.exists()) {
            try {
                serializer.decode<UserSettings>(file.readText())
            } catch (e: Exception) {
                UserSettings()
            }
        } else {
            UserSettings()
        }
    }

    fun saveUserSettings(settings: UserSettings) {
        val file = SettingsData.getUserSettingsFile()
        file.writeText(serializer.encode(settings))
    }

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

    fun saveProjectSettings(project: ProjectSettings): Boolean {
        return try {
            val file = project.getProjectFile()
            file.writeText(serializer.encode(project))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun createProject(
        name: String,
        folder: File,
        engineVersion: String
    ): Result<ProjectSettings> {
        return try {
            val projectFile = File(folder, "$name.skateproject")
            if (projectFile.exists()) {
                return Result.failure(IllegalStateException("Project '$name' already exists in this folder"))
            }

            val projectDir = File(folder, name)
            if (!projectDir.exists()) {
                projectDir.mkdirs()
            }

            File(projectDir, "Assets").mkdirs()
            File(projectDir, "Scenes").mkdirs()
            File(projectDir, "Builds").mkdirs()

            val metadata = ProjectMetadata(
                name = name,
                engineVersion = engineVersion,
                projectPath = File(projectDir, "$name.skateproject").absolutePath
            )

            val project = ProjectSettings(
                metadata = metadata,
                defaultScene = "Scenes/main.scene",
                assetPaths = listOf("Assets"),
                scenePaths = listOf("Scenes"),
                buildPaths = listOf("Builds")
            )

            saveProjectSettings(project)

            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
