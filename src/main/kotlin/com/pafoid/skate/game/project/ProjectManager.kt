package com.pafoid.skate.game.project

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.settings.ProjectSettings
import com.pafoid.skate.engine.settings.RecentProjectInfo
import com.pafoid.skate.engine.settings.SettingsData
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class ProjectManager(
    private val settingsManager: SettingsManager,
    private val serializer: Serializer,
    private val logger: LoggerService
) : KoinComponent {

    var currentProject: ProjectSettings? = null
        private set

    fun hasProject(): Boolean = currentProject != null

    fun getProjectName(): String = currentProject?.metadata?.name ?: "No Project"

    fun getRecentProjects(): List<RecentProjectInfo> {
        return settingsManager.recentProjects
    }

    fun loadLastProject(): Boolean {
        val recent = settingsManager.recentProjects.firstOrNull() ?: return false
        if (recent.path == lastClosedProjectPath) return false
        val projectFile = File(recent.path)
        if (!projectFile.exists()) return false
        return openProject(projectFile)
    }

    fun createProject(name: String, folder: File, engineVersion: String = "v0.46.0.1.19"): Result<ProjectSettings> {
        return try {
            logger.logEditor("Creating project: $name in ${folder.absolutePath}")

            val result = settingsManager.createProject(name, folder, engineVersion)

            result.onSuccess { project ->
                currentProject = project
                logger.logEditor("Project created successfully: ${project.metadata.name}")
            }

            result.onFailure { error ->
                logger.logEngine("Failed to create project: ${error.message}", LogLevel.ERROR)
            }

            result
        } catch (e: Exception) {
            logger.logEngine("Error creating project: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    fun openProject(projectFile: File): Boolean {
        return try {
            lastClosedProjectPath = null

            logger.logEditor("Opening project: ${projectFile.absolutePath}")

            if (!projectFile.exists()) {
                logger.logEngine("Project file does not exist: ${projectFile.absolutePath}", LogLevel.ERROR)
                return false
            }

            if (projectFile.extension != "skateproject") {
                logger.logEngine("Invalid project file extension: ${projectFile.name}", LogLevel.ERROR)
                return false
            }

            val success = settingsManager.loadProject(projectFile)

            if (success) {
                currentProject = settingsManager.project
                logger.logEditor("Project opened successfully: ${getProjectName()}")
            } else {
                logger.logEngine("Failed to load project: ${projectFile.absolutePath}", LogLevel.ERROR)
            }

            success
        } catch (e: Exception) {
            logger.logEngine("Error opening project: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    private var lastClosedProjectPath: String? = null

    fun closeProject() {
        val path = currentProject?.getProjectFile()?.absolutePath
        logger.logEditor("Closing project: ${getProjectName()}")
        currentProject = null
        lastClosedProjectPath = path
        settingsManager.closeProject()
    }

    fun saveProject(): Boolean {
        val project = currentProject ?: run {
            logger.logEditor("No project to save")
            return false
        }

        logger.logEditor("Saving project: ${project.metadata.name}")
        return settingsManager.saveProject(project)
    }

    fun getProjectDirectory(): File? {
        return currentProject?.getProjectDirectory()
    }

    fun getAssetsDirectory(): File? {
        return getProjectDirectory()?.let { projectDir ->
            File(projectDir, "Assets")
        }
    }

    fun getScenesDirectory(): File? {
        return getProjectDirectory()?.let { projectDir ->
            File(projectDir, "Scenes")
        }
    }

    fun getBuildsDirectory(): File? {
        return getProjectDirectory()?.let { projectDir ->
            File(projectDir, "Builds")
        }
    }

    fun isValidProjectStructure(projectDir: File): Boolean {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return false
        }

        val projectFiles = projectDir.listFiles { file ->
            file.extension == "skateproject"
        }

        return projectFiles != null && projectFiles.isNotEmpty()
    }

    fun findProjectsInDirectory(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        return directory.listFiles { file ->
            file.extension == "skateproject"
        }?.toList() ?: emptyList()
    }
}
