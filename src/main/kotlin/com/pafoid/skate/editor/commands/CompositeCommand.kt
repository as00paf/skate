package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene

/**
 * Several commands as one undo step (EDT-74: delete, duplicate, paste, reparent or edit a multi-selection).
 *
 * Each step is built just before it runs, the first time: a step that captures where its object is (a reparent's
 * old sibling index) sees the effect of the steps before it, so undo, which runs in reverse, restores the order
 * exactly. A step factory may return null to skip (e.g. an invalid reparent). Redo reruns the built commands.
 *
 * With a [scene], undo restores the selection from before the first execute, and [selectAfter] (given the built
 * commands) sets the selection after each execute, e.g. to every duplicated object.
 */
class CompositeCommand(
    override val displayNameKey: String,
    private val fallbackName: String,
    private val steps: List<() -> Command?>,
    private val scene: Scene? = null,
    private val selectAfter: ((List<Command>) -> List<GameObject>)? = null,
) : ExecutionTrackedCommand, KeyedCommand {
    private var built: List<Command>? = null
    private var selectionBefore: List<GameObject> = emptyList()

    /** The commands that ran, once executed. */
    val commands: List<Command> get() = built.orEmpty()

    override val displayNameArgs: List<Any> get() = listOf(commands.size)

    override fun execute() {
        val existing = built
        if (existing == null) {
            selectionBefore = scene?.selectedGameObjects.orEmpty()
            built = steps.mapNotNull { step -> step()?.also { it.execute() } }
        } else {
            existing.forEach { it.execute() }
        }
        selectAfter?.let { pick -> scene?.setSelection(pick(commands)) }
    }

    override fun undo() {
        commands.asReversed().forEach { it.undo() }
        scene?.setSelection(selectionBefore)
    }

    /** False when every step was skipped: nothing changed, so nothing goes into the history. */
    override fun wasSuccessful(): Boolean = commands.isNotEmpty()

    override fun getDisplayName(): String = "$fallbackName (${commands.size})"

    override fun getTargetName(): String? = commands.singleOrNull()?.getTargetName() ?: commands.firstOrNull()?.getTargetName()
}
