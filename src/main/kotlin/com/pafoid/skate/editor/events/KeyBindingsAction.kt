package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.input.BindingOperation
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.input.ProjectActionSet

sealed class KeyBindingsAction(eventName: String) : Event(eventName) {
    /** Runs [operation] as an undoable command and saves the project. */
    data class Apply(val operation: BindingOperation) : KeyBindingsAction("key_bindings.apply")

    /** Stores the current bindings as a named profile file in the project. */
    data class SaveProfile(val name: String) : KeyBindingsAction("key_bindings.save_profile")

    /**
     * Replaces the project's actions asset with [after] (INP-11) as one undoable edit, named by [labelKey] in
     * Command History.
     */
    data class EditProjectActions(
        val after: ProjectActionSet,
        val labelKey: String,
        val labelArgs: List<Any> = emptyList(),
    ) : KeyBindingsAction("key_bindings.edit_project_actions")

    data class DeleteProfile(val name: String) : KeyBindingsAction("key_bindings.delete_profile")
}
