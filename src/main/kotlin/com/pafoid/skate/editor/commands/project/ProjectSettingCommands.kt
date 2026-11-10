package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.Project

/** Sets the scene [project] opens with (EDT-57); undo restores the previous one. Saved with the project. */
class SetDefaultSceneCommand(private val project: Project, private val scenePath: String) : Command {
    private val before = project.defaultScene

    val isNoOp: Boolean get() = before == scenePath

    override fun execute() {
        project.defaultScene = scenePath
    }

    override fun undo() {
        project.defaultScene = before
    }

    override fun getDisplayName(): String = "Set Default Scene"
    override fun getTargetName(): String = project.name
}

/**
 * Sets [project]'s physics rate in steps per second (EDT-57) and applies it through [apply] (the engine's fixed
 * step); undo restores and re-applies the previous rate. Saved with the project.
 */
class SetPhysicsRateCommand(
    private val project: Project,
    private val stepsPerSecond: Int,
    private val apply: (Int) -> Unit,
) : Command {
    private val before = project.physicsFPS

    val isNoOp: Boolean get() = before == stepsPerSecond

    override fun execute() {
        project.physicsFPS = stepsPerSecond
        apply(stepsPerSecond)
    }

    override fun undo() {
        project.physicsFPS = before
        apply(before)
    }

    override fun getDisplayName(): String = "Set Physics Rate"
    override fun getTargetName(): String = project.name
}

/**
 * Sets the language the game starts in (EDT-57): [set] writes it to the project's `game.settings.json` and switches
 * the in-viewport game text. Undo puts [before] back.
 */
class SetGameLanguageCommand(
    private val projectName: String,
    private val before: String,
    private val locale: String,
    private val set: (String) -> Unit,
) : Command {
    val isNoOp: Boolean get() = before == locale

    override fun execute() = set(locale)

    override fun undo() = set(before)

    override fun getDisplayName(): String = "Set Game Language"
    override fun getTargetName(): String = projectName
}

/** Physics rates offered in Project Settings › Physics, in steps per second. */
object PhysicsRates {
    val CHOICES = listOf(30, 50, 60, 90, 120, 144, 240)

    /** The engine's fixed step for [stepsPerSecond]; anything below 1 falls back to 60 Hz. */
    fun fixedStep(stepsPerSecond: Int): Float = 1f / (if (stepsPerSecond >= 1) stepsPerSecond else 60)
}
