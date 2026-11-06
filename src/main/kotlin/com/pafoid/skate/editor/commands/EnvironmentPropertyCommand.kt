package com.pafoid.skate.editor.commands

/**
 * Command for changing a single environment property (time of day, light config values, etc.).
 * [displayNameKey] is a `command.environment.*` string key, translated by [CommandNames].
 */
class EnvironmentPropertyCommand<T>(
    override val displayNameKey: String,
    private val targetName: String? = null,
    private val setter: (T) -> Unit,
    private val oldValue: T,
    private val newValue: T
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
