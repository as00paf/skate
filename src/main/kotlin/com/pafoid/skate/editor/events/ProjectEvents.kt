package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.events.Event

sealed class ProjectEvent(eventName: String) : Event(eventName) {
    data class Opened(val project: Project) : ProjectEvent("editor.project_opened")
    data class Closed(val projectName: String) : ProjectEvent("editor.project_closed")
    data class Created(val project: Project) : ProjectEvent("editor.project_created")
    data class Saved(val project: Project) : ProjectEvent("editor.project_saved")
}
