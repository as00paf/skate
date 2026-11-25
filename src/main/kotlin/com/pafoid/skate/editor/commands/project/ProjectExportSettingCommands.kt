package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.physics3d.PhysicsBackend

/**
 * Sets [project]'s icon (EDT-91, AST-29): a project image or an `engine:` reference; blank falls back to the engine icon
 * at export (with a warning). Undo restores the previous icon. Saved with the project.
 */
class SetProjectIconCommand(private val project: Project, private val iconPath: String) : Command {
    private val before = project.iconPath

    val isNoOp: Boolean get() = before == iconPath

    override fun execute() { project.iconPath = iconPath }
    override fun undo() { project.iconPath = before }
    override fun getDisplayName(): String = "Set Project Icon"
    override fun getTargetName(): String = project.name
}

/**
 * Sets the exported game's splash (EDT-91, AST-29): [splashPath] (blank = the engine splash) shown for [seconds]
 * (0 = no splash, clamped by [Project.normalizedSplashSeconds]). Undo restores both.
 */
class SetProjectSplashCommand(private val project: Project, private val splashPath: String, seconds: Float) : Command {
    private val seconds = Project.normalizedSplashSeconds(seconds)
    private val beforePath = project.splashPath
    private val beforeSeconds = project.splashSeconds

    val isNoOp: Boolean get() = beforePath == splashPath && beforeSeconds == seconds

    override fun execute() { project.splashPath = splashPath; project.splashSeconds = seconds }
    override fun undo() { project.splashPath = beforePath; project.splashSeconds = beforeSeconds }
    override fun getDisplayName(): String = "Set Splash Screen"
    override fun getTargetName(): String = project.name
}

/** Sets the exported game's frame cap (EDT-91, AST-29): 0 is uncapped, else clamped to 15..240. */
class SetProjectTargetFpsCommand(private val project: Project, fps: Int) : Command {
    private val fps = Project.normalizedTargetFps(fps)
    private val before = project.targetFps

    val isNoOp: Boolean get() = before == fps

    override fun execute() { project.targetFps = fps }
    override fun undo() { project.targetFps = before }
    override fun getDisplayName(): String = "Set Target FPS"
    override fun getTargetName(): String = project.name
}

/** Sets the physics backend the exported game and Play use (EDT-91, AST-29). */
class SetProjectPhysicsBackendCommand(private val project: Project, private val backend: PhysicsBackend) : Command {
    private val before = project.physicsBackend

    val isNoOp: Boolean get() = before == backend

    override fun execute() { project.physicsBackend = backend }
    override fun undo() { project.physicsBackend = before }
    override fun getDisplayName(): String = "Set Physics Backend"
    override fun getTargetName(): String = project.name
}

/** Frame caps offered in the export dialog (EDT-91): 0 = uncapped; anything else is "Custom". */
object TargetFpsChoices {
    val PRESETS = listOf(0, 30, 60, 120, 144)

    fun isPreset(fps: Int): Boolean = fps in PRESETS
}
