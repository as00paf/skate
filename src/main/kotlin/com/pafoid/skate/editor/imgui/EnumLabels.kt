package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager

/**
 * Translated names for enum values shown in inspector combos (EDT-40): key `enum.<EnumClass>.<VALUE>`, falling back to
 * the constant's name when the table has none.
 */
object EnumLabels {
    fun key(value: Enum<*>): String = "enum.${value.declaringJavaClass.simpleName}.${value.name}"

    fun of(strings: StringManager, value: Enum<*>): String = key(value).let { if (strings.hasString(it)) strings.getString(it) else value.name }

    fun all(strings: StringManager, values: List<Enum<*>>): Array<String> = values.map { of(strings, it) }.toTypedArray()
}
