package com.pafoid.skate.editor.vcs

/** EDT-79 §4.4: how the shared diff view draws each line kind, free of ImGui. */
object DiffStyle {
    enum class Role { HUNK, ADDED, REMOVED, PLAIN }

    data class Style(val prefix: String, val role: Role)

    fun of(kind: DiffLine.Kind): Style = when (kind) {
        DiffLine.Kind.HUNK -> Style("", Role.HUNK)
        DiffLine.Kind.ADDED -> Style("+ ", Role.ADDED)
        DiffLine.Kind.REMOVED -> Style("- ", Role.REMOVED)
        DiffLine.Kind.CONTEXT -> Style("  ", Role.PLAIN)
    }

    /** RGBA of a [Role]; null keeps the theme's text colour. */
    fun color(role: Role): FloatArray? = when (role) {
        Role.HUNK -> floatArrayOf(0.45f, 0.7f, 1f, 1f)
        Role.ADDED -> floatArrayOf(0.45f, 0.9f, 0.45f, 1f)
        Role.REMOVED -> floatArrayOf(1f, 0.45f, 0.45f, 1f)
        Role.PLAIN -> null
    }

    /** "12 KB"-style size of a binary side, `-` when the file does not exist on that side. */
    fun size(bytes: Long?): String = when {
        bytes == null -> "-"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0))
    }
}
