package com.pafoid.skate.editor.scripting

import com.pafoid.skate.engine.scripting.host.ScriptError
import java.io.File

enum class DiagnosticSource { COMPILE, RUNTIME }

/** [line] and [column] are 1-based; [line] is null when the error has no position. */
data class ScriptDiagnostic(val line: Int?, val column: Int?, val message: String, val source: DiagnosticSource) {
    companion object {
        fun from(error: ScriptError, source: DiagnosticSource) =
            ScriptDiagnostic(error.line, error.column, error.message, source)
    }
}

/** UI-free model of one open script: text, dirty state and diagnostics. */
class ScriptDocument(val file: File, text: String) {
    var text: String = text
        private set
    private var savedText: String = text

    var diagnostics: List<ScriptDiagnostic> = emptyList()
        private set

    val name: String get() = file.name
    val isDirty: Boolean get() = text != savedText

    fun edit(newText: String) {
        text = newText
    }

    fun markSaved() {
        savedText = text
    }

    fun reloadFrom(newText: String) {
        text = newText
        savedText = newText
    }

    fun setDiagnostics(source: DiagnosticSource, errors: List<ScriptDiagnostic>) {
        diagnostics = diagnostics.filter { it.source != source } + errors
    }

    fun clearDiagnostics(source: DiagnosticSource) = setDiagnostics(source, emptyList())

    val lineCount: Int get() = text.count { it == '\n' } + 1

    /** Diagnostics for [line] (1-based), for inline display. */
    fun diagnosticsAt(line: Int): List<ScriptDiagnostic> = diagnostics.filter { it.line == line }

    /** 0-based character offset of the start of [line] (1-based), clamped to the text. */
    fun offsetOfLine(line: Int): Int {
        var offset = 0
        var current = 1
        while (current < line) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return text.length
            offset = next + 1
            current++
        }
        return offset
    }

    /** 1-based line containing the 0-based [offset]. */
    fun lineOfOffset(offset: Int): Int =
        text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' } + 1

    /** True when an error reported for [errorPath] (absolute or project-relative) concerns this file. */
    fun matchesPath(errorPath: String): Boolean {
        val a = file.path.replace('\\', '/')
        val b = errorPath.replace('\\', '/')
        return a == b || a.endsWith("/" + b.removePrefix("./"))
    }

    companion object {
        /** Resolves a `ScriptComponent.path` the way the runtime does, with the project directory as fallback base. */
        fun resolve(path: String, projectDir: File?): File {
            val direct = File(path)
            if (direct.isAbsolute || projectDir == null) return direct
            val inProject = File(projectDir, path)
            return if (inProject.exists() || !direct.exists()) inProject else direct
        }

        fun load(file: File): ScriptDocument = ScriptDocument(file, file.readText())
    }
}
