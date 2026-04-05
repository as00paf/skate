package com.pafoid.skate.game.project

import com.pafoid.skate.engine.settings.ProjectSettings
import java.io.File

/**
 * Represents a file or directory in the projected project structure.
 */
data class ProjectStructureItem(
    val name: String,
    val type: ItemType
)

/**
 * Type of project structure item.
 */
enum class ItemType {
    DIRECTORY,
    FILE
}

/**
 * Project creation dialog state management.
 *
 * This class handles the single-screen project creation dialog:
 * - Project name input with validation
 * - Project location selection with validation
 * - Resolution and quality settings
 * - Live preview of project structure
 *
 * ## Usage
 *
 * ```kotlin
 * val wizard = ProjectWizard()
 *
 * // Set project name
 * wizard.setProjectName("MyProject")
 *
 * // Set project location
 * wizard.setProjectLocation("/path/to/folder")
 *
 * // Check if project can be created
 * if (wizard.canCreate()) {
 *     val result = projectManager.createProject(
 *         wizard.projectName,
 *         File(wizard.projectPath)
 *     )
 * }
 * ```
 */
class ProjectWizard {

    /**
     * Whether the dialog is currently open.
     */
    val isOpen = imgui.type.ImBoolean(false)

    /**
     * Project name entered by user.
     */
    var projectName: String = ""
        private set

    /**
     * Project location/folder selected by user.
     */
    var projectPath: String = ""
        private set

    /**
     * Selected default resolution index.
     */
    var selectedResolution: Int = 0  // 0=1920x1080, 1=1280x720, 2=2560x1440
        private set

    /**
     * Selected graphics quality index.
     */
    var selectedQuality: Int = 2  // 0=LOW, 1=MEDIUM, 2=HIGH, 3=ULTRA
        private set

    /**
     * Available resolution options.
     */
    val resolutionOptions: List<String> = listOf(
        "1920 x 1080 (Full HD)",
        "1280 x 720 (HD)",
        "2560 x 1440 (2K)"
    )

    /**
     * Available graphics quality options.
     */
    val qualityOptions: List<String> = listOf(
        "Low",
        "Medium",
        "High",
        "Ultra"
    )

    /**
     * Set the project name.
     */
    fun setProjectName(name: String) {
        projectName = name.trim()
    }

    /**
     * Set the project location/folder.
     */
    fun setProjectLocation(path: String) {
        projectPath = path.trim()
    }

    /**
     * Set the selected resolution.
     */
    fun setSelectedResolution(index: Int) {
        selectedResolution = index.coerceIn(0, resolutionOptions.size - 1)
    }

    /**
     * Set the selected graphics quality.
     */
    fun setSelectedQuality(index: Int) {
        selectedQuality = index.coerceIn(0, qualityOptions.size - 1)
    }

    /**
     * Check if project can be created with current inputs.
     * Both name and path must be valid.
     */
    fun canCreate(): Boolean {
        return isProjectNameValid() && isProjectPathValid()
    }

    /**
     * Check if project name is valid.
     * Must be non-empty and contain only valid filesystem characters.
     */
    fun isProjectNameValid(): Boolean {
        if (projectName.isBlank()) return false

        // Check for invalid characters
        val invalidChars = Regex("[<>:\"/\\\\|?*]")
        return !invalidChars.containsMatchIn(projectName)
    }

    /**
     * Check if project path is valid.
     * Must exist, be a directory, and be writable.
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

    /**
     * Get the project directory.
     */
    fun getProjectDirectory(): File {
        return File(projectPath, projectName)
    }

    /**
     * Get the projected project structure items for rendering.
     */
    fun getProjectStructureItems(): List<ProjectStructureItem> {
        return listOf(
            ProjectStructureItem("Assets/", ItemType.DIRECTORY),
            ProjectStructureItem("Scenes/", ItemType.DIRECTORY),
            ProjectStructureItem("Builds/", ItemType.DIRECTORY),
            ProjectStructureItem("$projectName.skateproject", ItemType.FILE)
        )
    }

    /**
     * Get the projected project structure as display text (legacy).
     */
    @Deprecated("Use getProjectStructureItems instead")
    fun getProjectStructureText(): String {
        return buildString {
            appendLine("  [DIR]  Assets/")
            appendLine("  [DIR]  Scenes/")
            appendLine("  [DIR]  Builds/")
            appendLine("  [FILE] $projectName.skateproject")
        }
    }

    /**
     * Reset dialog to initial state.
     */
    fun reset() {
        projectName = ""
        projectPath = ""
        selectedResolution = 0
        selectedQuality = 2
    }
}
