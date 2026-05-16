package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.project.ProjectMetadata
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.serialization.Serializer
import java.io.File

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

    fun loadProjectSettings(projectFile: File): Project? {
        return if (projectFile.exists() && projectFile.extension == "skateproject") {
            try {
                serializer.decode<Project>(projectFile.readText())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun saveProjectSettings(project: Project): Boolean {
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
    ): Result<Project> {
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

            val project = Project(
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