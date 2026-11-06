package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.core.StringManager

/**
 * Translated command names for the Command History window (EDT-06).
 *
 * The name comes from `command.<CommandClass>` in the string tables; the history row already shows the target
 * (object, file) next to it, so names that only append the target use a plain verb ("Rename"). Commands without a
 * key, whose name carries other parameters (file moves, created primitive type, environment property, ...), keep
 * their [Command.getDisplayName].
 */
object CommandNames {
    fun key(command: Command): String = "command.${command.javaClass.simpleName}"

    fun displayName(command: Command, strings: StringManager): String {
        (command as? KeyedCommand)?.let { keyed ->
            if (strings.hasString(keyed.displayNameKey)) {
                val args = keyed.displayNameArgs
                return if (args.isEmpty()) strings.getString(keyed.displayNameKey) else strings.getString(keyed.displayNameKey, *args.toTypedArray())
            }
        }
        val key = key(command)
        return if (strings.hasString(key)) strings.getString(key) else command.getDisplayName()
    }

    /**
     * Translated target shown next to the name. Commands whose target is not an object or file (a window, the input
     * settings) return a string key from [Command.getTargetName]; object and file names are shown as they are.
     */
    fun targetName(command: Command, strings: StringManager): String? =
        command.getTargetName()?.let { if (strings.hasString(it)) strings.getString(it) else it }
}
