package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.events.Event

sealed class ProjectEvent(eventName: String) : Event(eventName) {
    data class Opened(val project: Project) : ProjectEvent("editor.project_opened")
    data class Closed(val projectName: String) : ProjectEvent("editor.project_closed")
    data class Created(val project: Project) : ProjectEvent("editor.project_created")
    data class Saved(val project: Project) : ProjectEvent("editor.project_saved")
    data class OpenProjectRequested(val projectPath: String) : ProjectEvent("project.action.open_project_requested")
    data class OpenProjectSucceeded(val projectPath: String) : ProjectEvent("project.action.open_project_succeeded")
    data class OpenProjectFailed(val projectPath: String, val reason: String) : ProjectEvent("project.action.open_project_failed")
    data class CreateProjectRequested(val name: String, val folderPath: String, val engineVersion: String) : ProjectEvent("project.action.create_project_requested")
    data class CreateProjectSucceeded(val name: String, val folderPath: String) : ProjectEvent("project.action.create_project_succeeded")
    data class CreateProjectFailed(val name: String, val folderPath: String, val reason: String) : ProjectEvent("project.action.create_project_failed")
    data class CreateFileRequested(val path: String, val isDirectory: Boolean) : ProjectEvent("project.action.create_file_requested")
    data class RenameFileRequested(val path: String, val newName: String) : ProjectEvent("project.action.rename_file_requested")
    data class DeleteFileRequested(val path: String) : ProjectEvent("project.action.delete_file_requested")
    object CloseProjectRequested : ProjectEvent("project.action.close_project_requested")
    object SaveProjectRequested : ProjectEvent("project.action.save_project_requested")
    object LoadLastProjectRequested : ProjectEvent("project.action.load_last_project_requested")
}
