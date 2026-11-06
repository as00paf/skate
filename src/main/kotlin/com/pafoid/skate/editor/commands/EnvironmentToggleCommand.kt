package com.pafoid.skate.editor.commands

/**
 * Command for toggling a boolean environment property (checkboxes).
 */
class EnvironmentToggleCommand(
    override val displayNameKey: String,
    private val targetName: String? = null,
    private val setter: (Boolean) -> Unit,
    private val oldValue: Boolean,
    private val newValue: Boolean
) : Command, KeyedCommand {
    override fun execute() {
        setter(newValue)
    }

    override fun undo() {
        setter(oldValue)
    }

    override fun getDisplayName(): String = displayNameKey
    override fun getTargetName(): String? = targetName
}
