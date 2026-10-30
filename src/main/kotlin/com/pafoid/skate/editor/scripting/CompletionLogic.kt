package com.pafoid.skate.editor.scripting

import com.pafoid.skate.engine.scripting.completion.Completion

/** A text change: replace [deleteCount] chars at [pos] with [insert]; the caret ends at [caretAfter]. */
data class TextEdit(val pos: Int, val deleteCount: Int, val insert: String, val caretAfter: Int) {
    fun apply(text: String): String = text.substring(0, pos) + insert + text.substring(pos + deleteCount)
}

/** UI-free state of the completion popup: filtering, selection and insertion. */
class CompletionSession(private val all: List<Completion>, private val anchor: Int, text: String, caret: Int) {
    var filtered: List<Completion> = emptyList()
        private set
    var selected = 0
        private set

    init { update(text, caret) }

    /** Text typed since the completion anchor. */
    fun prefix(text: String, caret: Int): String =
        if (caret < anchor || caret > text.length) "" else text.substring(anchor, caret)

    /** Re-filters after typing. Returns false when the popup should close (caret left the word or nothing matches). */
    fun update(text: String, caret: Int): Boolean {
        if (caret < anchor || caret > text.length) { filtered = emptyList(); return false }
        val prefix = prefix(text, caret)
        if (prefix.any { !(it.isLetterOrDigit() || it == '_') }) { filtered = emptyList(); return false }
        filtered = filter(all, prefix)
        selected = 0
        return filtered.isNotEmpty()
    }

    fun move(delta: Int) {
        if (filtered.isEmpty()) return
        selected = ((selected + delta) % filtered.size + filtered.size) % filtered.size
    }

    fun current(): Completion? = filtered.getOrNull(selected)

    /** The edit that inserts the selected item, replacing the typed prefix; snippets honour their cursor offset. */
    fun accept(caret: Int): TextEdit? {
        val item = current() ?: return null
        val start = if (item.replaceEnd >= item.replaceStart && item.replaceStart <= caret) item.replaceStart else anchor
        return TextEdit(start, caret - start, item.insertText, start + item.cursorOffset.coerceIn(0, item.insertText.length))
    }

    companion object {
        /** Prefix matches first, then substring, then subsequence; each group keeps provider order. */
        fun filter(items: List<Completion>, prefix: String): List<Completion> {
            if (prefix.isEmpty()) return items
            val p = prefix.lowercase()
            fun rank(c: Completion): Int {
                val l = c.label.lowercase()
                return when {
                    l.startsWith(p) -> 0
                    l.contains(p) -> 1
                    isSubsequence(p, l) -> 2
                    else -> 3
                }
            }
            return items.map { it to rank(it) }.filter { it.second < 3 }.sortedBy { it.second }.map { it.first }
        }

        private fun isSubsequence(p: String, s: String): Boolean {
            var i = 0
            for (c in s) if (i < p.length && p[i] == c) i++
            return i == p.length
        }

        /** Start of the identifier ending at [caret]. */
        fun wordStart(text: String, caret: Int): Int {
            var i = caret.coerceIn(0, text.length)
            while (i > 0 && (text[i - 1].isLetterOrDigit() || text[i - 1] == '_')) i--
            return i
        }

        /** True when typing [typed] (caret is after it) should pop the completion list automatically. */
        fun shouldAutoPopup(text: String, caret: Int, typed: Char): Boolean {
            if (typed == '.') return true
            if (!(typed.isLetter() || typed == '_')) return false
            return !isInsideCommentOrString(text, caret)
        }

        fun isInsideCommentOrString(text: String, caret: Int): Boolean {
            val lineStart = text.lastIndexOf('\n', caret - 1) + 1
            val line = text.substring(lineStart, caret.coerceAtMost(text.length))
            if (line.contains("//")) {
                val tokens = ScriptSyntax.tokenize(line)[0]
                if (tokens.lastOrNull()?.let { it.kind == TokenKind.COMMENT && it.end == line.length } == true) return true
            }
            return line.count { it == '"' } % 2 == 1
        }
    }
}

/** Closes brackets and quotes as the user types and skips over an existing closer. */
object BracketAutoClose {
    private val pairs = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"')

    /**
     * Called after [typed] was inserted just before [caret] in [text]. Returns an extra edit to apply, or null.
     * Typing a closer that already follows the caret removes the duplicate instead of adding one.
     */
    fun afterTyped(text: String, caret: Int, typed: Char): TextEdit? {
        val next = text.getOrNull(caret)
        if (typed in pairs.values && next == typed && (typed != '"' || text.getOrNull(caret - 2) != '\\')) {
            return TextEdit(caret, 1, "", caret)
        }
        val closer = pairs[typed] ?: return null
        if (typed == '"' && (text.getOrNull(caret - 2)?.let { it.isLetterOrDigit() || it == '"' } == true)) return null
        if (next != null && (next.isLetterOrDigit() || next == '_')) return null
        return TextEdit(caret, 0, closer.toString(), caret)
    }
}

data class ParameterHint(val functionName: String, val parameters: List<String>, val activeIndex: Int, val signature: String)

object ParameterHints {
    /** Finds the call enclosing [caret] and the argument being typed, using [signatures] (name to signature). */
    fun at(text: String, caret: Int, signatures: Map<String, String>): ParameterHint? {
        var depth = 0
        var commas = 0
        var i = caret.coerceAtMost(text.length) - 1
        while (i >= 0) {
            when (text[i]) {
                ')', ']', '}' -> depth++
                '(' -> if (depth == 0) {
                    val end = i
                    var s = end
                    while (s > 0 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '_')) s--
                    val name = text.substring(s, end)
                    val signature = signatures[name] ?: return null
                    return ParameterHint(name, parameters(signature), commas, signature)
                } else depth--
                '[', '{' -> if (depth > 0) depth-- else return null
                ',' -> if (depth == 0) commas++
            }
            i--
        }
        return null
    }

    fun parameters(signature: String): List<String> {
        val open = signature.indexOf('(')
        if (open < 0) return emptyList()
        var depth = 0
        val result = ArrayList<String>()
        val current = StringBuilder()
        for (i in open + 1 until signature.length) {
            val c = signature[i]
            if (c == ')' && depth == 0) break
            if (c == '(' || c == '<') depth++
            if (c == ')' || c == '>') depth--
            if (c == ',' && depth == 0) { result.add(current.toString().trim()); current.clear() } else current.append(c)
        }
        if (current.isNotBlank()) result.add(current.toString().trim())
        return result
    }
}
