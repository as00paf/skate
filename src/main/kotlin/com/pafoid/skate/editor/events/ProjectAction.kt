package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class ProjectAction(eventName: String) : Event(eventName)

data class CreateFileRequested(val path: String, val isDirectory: Boolean) :
    ProjectAction("project.action.create_file_requested")

data class RenameFileRequested(val path: String, val newName: String) :
    ProjectAction("project.action.rename_file_requested")

data class DeleteFileRequested(val path: String) :
    ProjectAction("project.action.delete_file_requested")
