package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.events.Event

sealed class ProjectEvent(eventName: String) : Event(eventName)

data class ProjectOpened(val project: Project) : ProjectEvent("editor.project_opened")
data class ProjectClosed(val projectName: String) : ProjectEvent("editor.project_closed")
data class ProjectCreated(val project: Project) : ProjectEvent("editor.project_created")
data class ProjectSaved(val project: Project) : ProjectEvent("editor.project_saved")
