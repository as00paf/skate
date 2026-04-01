package com.pafoid.skate.game.project

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.LoggerService.LogLevel
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.settings.ProjectSettings
import com.pafoid.skate.engine.settings.RecentProjectInfo
import com.pafoid.skate.engine.settings.SettingsData
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Manages project lifecycle including creation, opening, closing, and recent projects.
 * 
 * ## Usage
 * 
 * ```kotlin
 * val projectManager: ProjectManager by inject()
 * 
 * // Create new project
 * val result = projectManager.createProject("MyProject", File("/path/to/folder"))
 * 
 * // Open existing project
 * val success = projectManager.openProject(File("/path/to/project.skateproject"))
 * 
 * // Get current project
 * val currentProject = projectManager.currentProject
 * 
 * // Get recent projects
 * val recent = projectManager.getRecentProjects()
 * ```
 * 
 * @param settingsManager Settings manager for persisting recent projects
 * @param serializer JSON serializer for project files
 * @param logger Logger for error reporting
 */
class ProjectManager(
    private val settingsManager: SettingsManager,
    private val serializer: Serializer,
    private val logger: LoggerService
) : KoinComponent {
    
    /**
     * Currently loaded project (null if no project is open).
     */
    var currentProject: ProjectSettings? = null
        private set
    
    /**
     * Check if a project is currently loaded.
     */
    fun hasProject(): Boolean = currentProject != null
    
    /**
     * Get the current project name or "No Project" if none loaded.
     */
    fun getProjectName(): String = currentProject?.metadata?.name ?: "No Project"
    
    /**
     * Get list of recent projects (up to 5).
     */
    fun getRecentProjects(): List<RecentProjectInfo> {
        return settingsManager.recentProjects
    }
    
    /**
     * Create a new project with the given name in the specified folder.
     * 
     * @param name Project name
     * @param folder Parent folder where project will be created
     * @param engineVersion Engine version to store in project metadata
     * @return Result with ProjectSettings on success, error on failure
     */
    fun createProject(name: String, folder: File, engineVersion: String = "v0.46.0.1.19"): Result<ProjectSettings> {
        return try {
            logger.logEditor("Creating project: $name in ${folder.absolutePath}")
            
            // Create project via SettingsManager
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
    
    /**
     * Open an existing project from a .skateproject file.
     * 
     * @param projectFile Path to the .skateproject file
     * @return true if project opened successfully, false otherwise
     */
    fun openProject(projectFile: File): Boolean {
        return try {
            logger.logEditor("Opening project: ${projectFile.absolutePath}")
            
            if (!projectFile.exists()) {
                logger.logEngine("Project file does not exist: ${projectFile.absolutePath}", LogLevel.ERROR)
                return false
            }
            
            if (projectFile.extension != "skateproject") {
                logger.logEngine("Invalid project file extension: ${projectFile.name}", LogLevel.ERROR)
                return false
            }
            
            // Load project via SettingsManager
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
    
    /**
     * Close the current project.
     * 
     * This does not save the project - call saveProject() first if needed.
     */
    fun closeProject() {
        logger.logEditor("Closing project: ${getProjectName()}")
        currentProject = null
        settingsManager.closeProject()
    }
    
    /**
     * Save the current project.
     * 
     * @return true if saved successfully, false if no project is loaded or save failed
     */
    fun saveProject(): Boolean {
        val project = currentProject ?: run {
            logger.logEditor("No project to save")
            return false
        }
        
        logger.logEditor("Saving project: ${project.metadata.name}")
        settingsManager.saveProject(project)
        return true
    }
    
    /**
     * Get the project directory for the current project.
     * 
     * @return Project directory File, or null if no project is loaded
     */
    fun getProjectDirectory(): File? {
        return currentProject?.getProjectDirectory()
    }
    
    /**
     * Get the assets directory for the current project.
     * 
     * @return Assets directory File, or null if no project is loaded
     */
    fun getAssetsDirectory(): File? {
        return getProjectDirectory()?.let { projectDir ->
            File(projectDir, "Assets")
        }
    }
    
    /**
     * Get the scenes directory for the current project.
     * 
     * @return Scenes directory File, or null if no project is loaded
     */
    fun getScenesDirectory(): File? {
        return getProjectDirectory()?.let { projectDir ->
            File(projectDir, "Scenes")
        }
    }
    
    /**
     * Get the builds directory for the current project.
     * 
     * @return Builds directory File, or null if no project is loaded
     */
    fun getBuildsDirectory(): File? {
        return getProjectDirectory()?.let { projectDir ->
            File(projectDir, "Builds")
        }
    }
    
    /**
     * Validate that a project folder structure is valid.
     * 
     * @param projectDir Project directory to validate
     * @return true if valid project structure, false otherwise
     */
    fun isValidProjectStructure(projectDir: File): Boolean {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return false
        }
        
        // Check for .skateproject file
        val projectFiles = projectDir.listFiles { file ->
            file.extension == "skateproject"
        }
        
        return projectFiles != null && projectFiles.isNotEmpty()
    }
    
    /**
     * Get a list of potential projects in a directory.
     * 
     * @param directory Directory to search
     * @return List of project files found
     */
    fun findProjectsInDirectory(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }
        
        return directory.listFiles { file ->
            file.extension == "skateproject"
        }?.toList() ?: emptyList()
    }
}
