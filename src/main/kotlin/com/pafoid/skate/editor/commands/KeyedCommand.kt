package com.pafoid.skate.editor.commands

/**
 * A command whose shown name depends on how it was created (one class, several actions), so it cannot use the
 * per-class `command.<CommandClass>` key. [CommandNames] translates [displayNameKey] first, formatted with
 * [displayNameArgs] (for names that carry a parameter, such as the edited action id).
 */
interface KeyedCommand {
    val displayNameKey: String
    val displayNameArgs: List<Any> get() = emptyList()
}
