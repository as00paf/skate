package com.pafoid.skate.editor.vcs

/**
 * EDT-60 §7.2: a scene or prefab with Git conflict markers is not JSON. Loaders check for markers first, so the user
 * sees "resolve the conflict" instead of a JSON parse error (this also covers merges made outside the editor).
 */
object ConflictMarkers {
    private val marker = Regex("""^(<{7}|>{7})( |$)""", RegexOption.MULTILINE)

    fun hasMarkers(text: String): Boolean = marker.containsMatchIn(text)

    /** Returns [text], or throws [ConflictMarkersException] when [text] of [fileName] holds conflict markers. */
    fun requireClean(fileName: String, text: String): String {
        if (hasMarkers(text)) throw ConflictMarkersException(fileName)
        return text
    }
}

class ConflictMarkersException(val fileName: String) :
    IllegalStateException("$fileName has unresolved Git conflict markers; resolve it in Version Control (Keep mine or Take theirs) first")
