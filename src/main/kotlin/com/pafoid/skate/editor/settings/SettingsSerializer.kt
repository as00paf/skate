package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.serialization.Serializer
import java.io.File

class SettingsSerializer(private val serializer: Serializer) {

    fun loadEngineSettings(): EditorSettings {
        val file = SettingsData.getEngineSettingsFile()
        return if (file.exists()) {
            try {
                serializer.decode<EditorSettings>(file.readText())
            } catch (e: Exception) {
                EditorSettings()
            }
        } else {
            EditorSettings()
        }
    }

    fun saveEngineSettings(settings: EditorSettings) {
        val file = SettingsData.getEngineSettingsFile()
        file.writeText(serializer.encode(settings))
    }

    fun loadProjectSettings(projectFile: File): Project? {
        return if (projectFile.exists() && projectFile.extension in FileType.PROJECT_FILE.extensions) {
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
}