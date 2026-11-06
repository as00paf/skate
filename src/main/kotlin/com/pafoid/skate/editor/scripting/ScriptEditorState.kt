package com.pafoid.skate.editor.scripting

/** UI-free text search for the script editor's find/replace bar (EDT-55). Offsets are 0-based characters. */
object TextSearch {
    /** Start offsets of every non-overlapping match of [query], in order. */
    fun findAll(text: String, query: String, matchCase: Boolean = false): List<Int> {
        if (query.isEmpty()) return emptyList()
        val result = ArrayList<Int>()
        var from = 0
        while (true) {
            val at = text.indexOf(query, from, ignoreCase = !matchCase)
            if (at < 0) return result
            result += at
            from = at + query.length
        }
    }

    /**
     * The match after [from] (or before it when [backwards]), wrapping around the text. Forward search returns a match
     * starting at or after [from]; backward search one starting strictly before [from]. Null when there is no match.
     */
    fun next(text: String, query: String, from: Int, matchCase: Boolean = false, backwards: Boolean = false): Int? {
        val matches = findAll(text, query, matchCase)
        if (matches.isEmpty()) return null
        return if (backwards) matches.lastOrNull { it < from } ?: matches.last()
        else matches.firstOrNull { it >= from } ?: matches.first()
    }

    /** Replaces the match starting at [at], or returns null when [query] does not match there. */
    fun replaceAt(text: String, query: String, replacement: String, at: Int, matchCase: Boolean = false): TextEdit? {
        if (query.isEmpty() || at < 0 || at + query.length > text.length) return null
        if (!text.regionMatches(at, query, 0, query.length, ignoreCase = !matchCase)) return null
        return TextEdit(at, query.length, replacement, at + replacement.length)
    }

    /** Text with every match replaced, and the number of replacements. */
    fun replaceAll(text: String, query: String, replacement: String, matchCase: Boolean = false): Pair<String, Int> {
        val matches = findAll(text, query, matchCase)
        if (matches.isEmpty()) return text to 0
        val out = StringBuilder(text.length)
        var pos = 0
        for (m in matches) {
            out.append(text, pos, m).append(replacement)
            pos = m + query.length
        }
        out.append(text, pos, text.length)
        return out.toString() to matches.size
    }
}

/** Parses the go-to-line input (EDT-55): `12` or `12:5` (line:column, 1-based), clamped to the document. */
object GoToLine {
    data class Target(val line: Int, val column: Int)

    fun parse(input: String, lineCount: Int): Target? {
        val parts = input.trim().split(':')
        if (parts.size > 2) return null
        val line = parts[0].trim().toIntOrNull() ?: return null
        val column = if (parts.size == 2) parts[1].trim().toIntOrNull() ?: return null else 1
        if (line < 1 || column < 1) return null
        return Target(line.coerceAtMost(maxOf(lineCount, 1)), column)
    }

    /** 0-based offset of [target] in [text]; the column is clamped to the end of its line. */
    fun offset(text: String, target: Target): Int {
        var start = 0
        repeat(target.line - 1) {
            val next = text.indexOf('\n', start)
            if (next < 0) return text.length
            start = next + 1
        }
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        return (start + target.column - 1).coerceAtMost(end)
    }
}

/** One gutter marker: the line's diagnostics, compile errors first. */
data class GutterMarker(val line: Int, val diagnostics: List<ScriptDiagnostic>) {
    val source: DiagnosticSource get() = diagnostics.first().source
    val tooltip: String get() = diagnostics.joinToString("\n") { it.message.lineSequence().first() }
}

/** Error gutter markers per line (EDT-55); diagnostics without a line are only in the error list. */
fun ScriptDocument.gutterMarkers(): Map<Int, GutterMarker> =
    diagnostics.filter { it.line != null }
        .groupBy { it.line!! }
        .mapValues { (line, list) -> GutterMarker(line, list.sortedBy { it.source.ordinal }) }

/**
 * Open tabs of the script editor (EDT-55): open, select, close, and a confirmation step before a tab with unsaved
 * changes is closed.
 */
class ScriptTabs {
    private val open = ArrayList<ScriptDocument>()
    val documents: List<ScriptDocument> get() = open

    var active: ScriptDocument? = null
        private set

    /** Tab the UI must select on its next frame (set when a document is opened or a tab is closed). */
    var selectRequest: ScriptDocument? = null

    /** A dirty tab waiting for the user to save or discard it. */
    var pendingClose: ScriptDocument? = null
        private set

    val hasUnsaved: Boolean get() = open.any { it.isDirty }

    /** Shows [doc], adding a tab unless one for the same file is already open. Returns the shown document. */
    fun open(doc: ScriptDocument): ScriptDocument {
        val shown = find(doc.file) ?: doc.also { open += it }
        active = shown
        selectRequest = shown
        return shown
    }

    fun find(file: java.io.File): ScriptDocument? = open.find { it.file.absoluteFile == file.absoluteFile }

    fun select(doc: ScriptDocument) {
        if (doc in open) active = doc
    }

    /** Closes [doc] now if it has no unsaved changes; otherwise asks for confirmation through [pendingClose]. */
    fun requestClose(doc: ScriptDocument): Boolean {
        if (doc.isDirty) {
            pendingClose = doc
            return false
        }
        close(doc)
        return true
    }

    /** Closes the pending tab (its unsaved changes are dropped). */
    fun discardPending() {
        pendingClose?.let { close(it) }
        pendingClose = null
    }

    fun cancelPending() {
        pendingClose = null
    }

    private fun close(doc: ScriptDocument) {
        val index = open.indexOf(doc)
        if (index < 0) return
        open.removeAt(index)
        if (active === doc) {
            active = open.getOrNull(index) ?: open.lastOrNull()
            selectRequest = active
        }
    }

    /** Tab label: `* ` for unsaved changes, `! ` for errors, and the file path as stable ImGui ID. */
    fun label(doc: ScriptDocument): String {
        val marks = (if (doc.isDirty) "* " else "") + (if (doc.diagnostics.isNotEmpty()) "! " else "")
        return marks + doc.name + "###" + doc.file.absolutePath
    }
}
