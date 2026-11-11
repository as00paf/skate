package com.pafoid.skate.editor.commands.animation

import com.pafoid.skate.editor.animation.controllereditor.ControllerDocument
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.KeyedCommand
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset

/** The edits of the Animator window (ANI-21 §3.2); [key] is the action part of `command.ControllerEditCommand.<key>`. */
enum class ControllerAction(val key: String) {
    ADD_STATE("add_state"),
    RENAME_STATE("rename_state"),
    DUPLICATE_STATE("duplicate_state"),
    DELETE_STATE("delete_state"),
    SET_DEFAULT("set_default"),
    MOVE_STATES("move_states"),
    SET_MOTION("set_motion"),
    SET_STATE("set_state"),
    ADD_EVENT("add_event"),
    REMOVE_EVENT("remove_event"),
    ADD_TRANSITION("add_transition"),
    SET_TRANSITION("set_transition"),
    MOVE_TRANSITION("move_transition"),
    REMOVE_TRANSITION("remove_transition"),
    ADD_PARAMETER("add_parameter"),
    RENAME_PARAMETER("rename_parameter"),
    SET_PARAMETER("set_parameter"),
    REMOVE_PARAMETER("remove_parameter"),
    SET_CONTROLLER("set_controller"),
    PASTE_STATES("paste_states"),
}

/**
 * One edit of a controller in the Animator window (ANI-21 §5.1). It swaps the whole [AnimationControllerAsset]
 * (immutable and small, already validated by `ControllerEditor`), so every action undoes the same way. A drag is
 * previewed live and committed once on release, so it is one undo step.
 */
class ControllerEditCommand(
    private val document: ControllerDocument,
    private val before: AnimationControllerAsset,
    private val after: AnimationControllerAsset,
    val action: ControllerAction,
) : Command, KeyedCommand {
    override fun execute() = document.replace(after)
    override fun undo() = document.replace(before)
    override fun getDisplayName(): String = action.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    override fun getTargetName(): String = document.displayName
    override val displayNameKey: String get() = "command.ControllerEditCommand.${action.key}"
}
