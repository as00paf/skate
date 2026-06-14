package com.pafoid.skate.editor.project

import java.io.File

class ProjectWizard {

    companion object {
        private val INVALID_NAME_CHARS = Regex("[<>:\"/\\\\|?*]")
    }

    var projectName: String = ""
        private set

    var projectPath: String = ""
        private set


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
        projectName = ""
        projectPath = ""
    }
}
