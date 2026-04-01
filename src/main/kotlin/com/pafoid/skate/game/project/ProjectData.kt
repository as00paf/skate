package com.pafoid.skate.game.project

import com.pafoid.skate.engine.settings.ProjectSettings
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Data models for project management.
 */

/**
 * Project creation result with project settings and status.
 * 
 * @param success Whether project creation was successful
 * @param project The created project settings (null if failed)
 * @param errorMessage Error message if creation failed
 */
data class ProjectCreationResult(
    val success: Boolean,
    val project: ProjectSettings? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(project: ProjectSettings): ProjectCreationResult {
            return ProjectCreationResult(success = true, project = project)
        }
        
        fun failure(errorMessage: String): ProjectCreationResult {
            return ProjectCreationResult(success = false, errorMessage = errorMessage)
        }
    }
}

/**
 * Project open result with project settings and status.
 * 
 * @param success Whether project open was successful
 * @param project The opened project settings (null if failed)
 * @param errorMessage Error message if open failed
 */
data class ProjectOpenResult(
    val success: Boolean,
    val project: ProjectSettings? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(project: ProjectSettings): ProjectOpenResult {
            return ProjectOpenResult(success = true, project = project)
        }
        
        fun failure(errorMessage: String): ProjectOpenResult {
            return ProjectOpenResult(success = false, errorMessage = errorMessage)
        }
    }
}

/**
 * Wizard step data for project creation wizard.
 * 
 * @param step Current wizard step (0-3)
 * @param projectName Name of the project
 * @param projectPath Path where project will be created
 * @param isValid Whether current step data is valid
 */
data class WizardStepData(
    val step: Int = 0,
    val projectName: String = "",
    val projectPath: String = "",
    val isValid: Boolean = false
) {
    /**
     * Check if project name is valid.
     * Must be non-empty and contain only valid characters.
     */
    fun isProjectNameValid(): Boolean {
        if (projectName.isBlank()) return false
        
        // Check for invalid characters
        val invalidChars = Regex("[<>:\"/\\\\|?*]")
        return !invalidChars.containsMatchIn(projectName)
    }
    
    /**
     * Check if project path is valid.
     * Must exist and be writable.
     */
    fun isProjectPathValid(): Boolean {
        if (projectPath.isBlank()) return false
        
        val folder = File(projectPath)
        return folder.exists() && folder.isDirectory && folder.canWrite()
    }
    
    /**
     * Get the full project file path.
     */
    fun getProjectFilePath(): String {
        return File(projectPath, "$projectName.skateproject").absolutePath
    }
}

/**
 * Recent project information for UI display.
 * 
 * @param name Project name
 * @param path Full path to .skateproject file
 * @param lastOpened Last opened timestamp
 * @param exists Whether project still exists at path
 */
data class RecentProjectDisplayInfo(
    val name: String,
    val path: String,
    val lastOpened: Long,
    val exists: Boolean
) {
    /**
     * Get formatted last opened date string.
     */
    fun getLastOpenedString(): String {
        val now = System.currentTimeMillis()
        val diff = now - lastOpened
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} minutes ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            diff < 604800000 -> "${diff / 86400000} days ago"
            else -> {
                val date = java.util.Date(lastOpened)
                val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                format.format(date)
            }
        }
    }
}
