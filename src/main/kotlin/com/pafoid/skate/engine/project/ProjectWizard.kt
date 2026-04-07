package com.pafoid.skate.engine.project

import imgui.type.ImBoolean
import java.io.File

data class ProjectStructureItem(
    val name: String,
    val type: ItemType
)

enum class ItemType {
    DIRECTORY,
    FILE
}

class ProjectWizard {

    companion object {
        private val INVALID_NAME_CHARS = Regex("[<>:\"/\\\\|?*]")
    }

    private val _isOpen = ImBoolean(false)
    val isOpen: ImBoolean get() = _isOpen
    val isCurrentlyOpen: Boolean get() = _isOpen.get()

    var userDismissed: Boolean = false
        private set

    var projectName: String = ""
        private set

    var projectPath: String = ""
        private set

    fun open() {
        userDismissed = false
        _isOpen.set(true)
    }

    fun dismiss() {
        userDismissed = true
        _isOpen.set(false)
    }

    fun setProjectName(name: String) {
        projectName = name.trim()
    }

    fun setProjectLocation(path: String) {
        projectPath = path.trim()
    }

    fun canCreate(): Boolean {
        return isProjectNameValid() && isProjectPathValid()
    }

    fun isProjectNameValid(): Boolean {
        if (projectName.isBlank()) return false
        return !INVALID_NAME_CHARS.containsMatchIn(projectName)
    }

    fun isProjectPathValid(): Boolean {
        if (projectPath.isBlank()) return false

        val folder = File(projectPath)
        return folder.exists() && folder.isDirectory && folder.canWrite()
    }

    fun getProjectFilePath(): String {
        return File(projectPath, "$projectName.skateproject").absolutePath
    }

    fun getProjectDirectory(): File {
        return File(projectPath, projectName)
    }

    fun getProjectStructureItems(): List<ProjectStructureItem> {
        return listOf(
            ProjectStructureItem("Assets/", ItemType.DIRECTORY),
            ProjectStructureItem("Scenes/", ItemType.DIRECTORY),
            ProjectStructureItem("  main.scene", ItemType.FILE),
            ProjectStructureItem("Builds/", ItemType.DIRECTORY),
            ProjectStructureItem("$projectName.skateproject", ItemType.FILE)
        )
    }

    fun reset() {
        projectName = ""
        projectPath = ""
    }

    fun resetForNewProject() {
        userDismissed = false
        projectName = ""
        projectPath = ""
    }
}
