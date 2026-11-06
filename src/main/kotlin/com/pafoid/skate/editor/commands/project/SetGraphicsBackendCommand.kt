package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.render.rhi.GraphicsBackend

/**
 * Sets the graphics backend exported games of [project] start with (EDT-43); undo restores the previous one. The
 * editor itself always renders with OpenGL. Saving the project writes it to the `.skateproject`.
 */
class SetGraphicsBackendCommand(private val project: Project, private val backend: GraphicsBackend) : Command {
    private val before = project.graphicsBackend

    val isNoOp: Boolean get() = before == backend

    override fun execute() {
        project.graphicsBackend = backend
    }

    override fun undo() {
        project.graphicsBackend = before
    }

    override fun getDisplayName(): String = "Set Graphics Backend"
    override fun getTargetName(): String = project.name
}
