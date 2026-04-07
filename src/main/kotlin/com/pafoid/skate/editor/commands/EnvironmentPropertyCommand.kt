package com.pafoid.skate.editor.commands

/**
 * Command for changing a single environment property (time of day, light config values, etc.).
 */
class EnvironmentPropertyCommand<T>(
    private val displayName: String,
    private val targetName: String? = null,
    private val setter: (T) -> Unit,
    private val oldValue: T,
    private val newValue: T
) : Command {
    override fun execute() {
        setter(newValue)
    }

    override fun undo() {
        setter(oldValue)
    }

    override fun getDisplayName(): String = displayName
    override fun getTargetName(): String? = targetName
}