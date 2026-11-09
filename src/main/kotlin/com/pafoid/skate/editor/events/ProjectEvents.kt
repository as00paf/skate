package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.ExportProgress
import com.pafoid.skate.engine.utils.ExportResult

sealed class ProjectEvent(eventName: String) : Event(eventName) {
    data class Opened(val project: Project) : ProjectEvent("editor.project_opened")
    /** Published (synchronously) before a project's scenes are saved and closed: Play stops here (EDT-45). */
    data class Closing(val project: Project) : ProjectEvent("editor.project_closing")
    data class Closed(val project: Project) : ProjectEvent("editor.project_closed")
    data class Created(val project: Project) : ProjectEvent("editor.project_created")
    data class Saved(val project: Project) : ProjectEvent("editor.project_saved")
    object OpenProjectFileRequested : ProjectEvent("project.action.open_project_file_requested")
    data class OpenProjectRequested(val projectPath: String) :
        ProjectEvent("project.action.open_project_requested")

    data class OpenProjectSucceeded(val projectPath: String?) : ProjectEvent("project.action.open_project_succeeded")
    data class OpenProjectFailed(val projectPath: String?, val reason: String) :
        ProjectEvent("project.action.open_project_failed")
    data class CreateProjectRequested(
        val name: String,
        val folderPath: String,
        val graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT,
    ) : ProjectEvent("project.action.create_project_requested")

    /** Change the open project's graphics backend (EDT-43), as an undoable command. */
    data class SetGraphicsBackendRequested(val backend: GraphicsBackend) : ProjectEvent("project.action.set_graphics_backend")
    /** Change the open project's overall graphics quality preset (REN-30). */
    data class SetGraphicsQualityRequested(val quality: com.pafoid.skate.editor.settings.GraphicsQuality) : ProjectEvent("project.action.set_graphics_quality")
    /** Change the open project's MSAA sample count (REN-30). */
    data class SetMsaaSamplesRequested(val samples: Int) : ProjectEvent("project.action.set_msaa_samples")
    /** Change the open project's texture quality (REN-30). */
    data class SetTextureQualityRequested(val quality: com.pafoid.skate.editor.settings.TextureQuality) : ProjectEvent("project.action.set_texture_quality")
    /** Change the open project's shadow quality (REN-30). */
    data class SetShadowQualityRequested(val quality: com.pafoid.skate.editor.settings.ShadowQuality) : ProjectEvent("project.action.set_shadow_quality")
    /** Change the open project's fullscreen setting (GUI-15). */
    data class SetFullscreenRequested(val enabled: Boolean) : ProjectEvent("project.action.set_fullscreen")
    /** Change the open project's VSync setting (GUI-15). */
    data class SetVsyncRequested(val enabled: Boolean) : ProjectEvent("project.action.set_vsync")
    data class CreateProjectSucceeded(val name: String, val folderPath: String) : ProjectEvent("project.action.create_project_succeeded")
    data class CreateProjectFailed(val name: String, val folderPath: String, val reason: String) : ProjectEvent("project.action.create_project_failed")
    data class CreateFileRequested(val path: String, val isDirectory: Boolean) : ProjectEvent("project.action.create_file_requested")
    data class RenameFileRequested(val path: String, val newName: String) : ProjectEvent("project.action.rename_file_requested")
    data class MoveFileRequested(val path: String, val targetDir: String) : ProjectEvent("project.action.move_file_requested")
    data class DeleteFileRequested(val path: String) : ProjectEvent("project.action.delete_file_requested")
    object CloseProjectRequested : ProjectEvent("project.action.close_project_requested")
    object SaveRequested : ProjectEvent("project.action.save_project_requested")
    object SaveAsRequested : ProjectEvent("project.action.save_project_as_requested")
    object LoadLastProjectRequested : ProjectEvent("project.action.load_last_project_requested")

    /** Saves the project, then exports it in the background with [options] (EDT-09 export wizard). */
    data class ExportRequested(val options: ExportOptions = ExportOptions()) : ProjectEvent("project.action.export_project_requested")
    /** Published on the main thread for every export step while an export runs. */
    data class ExportProgressed(val progress: ExportProgress) : ProjectEvent("project.action.export_progressed")
    /** Published on the main thread when an export ends, successful or not. */
    data class ExportFinished(val result: ExportResult) : ProjectEvent("project.action.export_finished")
}
