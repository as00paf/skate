package com.pafoid.skate.game.project

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
     * Whether the user explicitly dismissed the dialog.
     * Prevents auto-reopen after the user clicks Cancel.
     */
    var userDismissed: Boolean = false
        private set

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
     * Open the dialog (clears dismissal flag).
     */
    fun open() {
        userDismissed = false
        isOpen.set(true)
    }

    /**
     * Close the dialog and mark as explicitly dismissed.
     */
    fun dismiss() {
        userDismissed = true
        isOpen.set(false)
    }

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
     * Reset dialog to initial state without changing the dismissal flag.
     */
    fun reset() {
        projectName = ""
        projectPath = ""
    }

    /**
     * Reset dialog fully (used when closing a project to allow wizard to appear again).
     */
    fun resetForNewProject() {
        userDismissed = false
        projectName = ""
        projectPath = ""
    }
}
