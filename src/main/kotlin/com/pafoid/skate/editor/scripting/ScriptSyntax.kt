package com.pafoid.skate.editor.scripting

enum class TokenKind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION }

data class Token(val start: Int, val end: Int, val kind: TokenKind)

/** Minimal Kotlin lexer for highlighting. Tokens are per line; block comments and raw strings carry state across lines. */
object ScriptSyntax {
    val KEYWORDS: Set<String> = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
        "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "val", "var",
        "when", "while", "import", "override", "private", "public", "internal", "const", "lateinit", "by", "catch",
        "finally", "data", "enum", "sealed", "open", "abstract", "companion", "init", "it",
    )

    private enum class State { NONE, BLOCK_COMMENT, RAW_STRING }

    /** Tokenizes [text] and returns the tokens of each line, with offsets relative to that line. */
    fun tokenize(text: String): List<List<Token>> {
        var state = State.NONE
        return text.split('\n').map { raw ->
            val line = raw.removeSuffix("\r")
            val tokens = ArrayList<Token>()
            var i = 0
            fun add(start: Int, end: Int, kind: TokenKind) {
                if (end > start) tokens.add(Token(start, end, kind))
            }
            while (i < line.length) {
                when (state) {
                    State.BLOCK_COMMENT -> {
                        val close = line.indexOf("*/", i)
                        if (close < 0) { add(i, line.length, TokenKind.COMMENT); i = line.length }
                        else { add(i, close + 2, TokenKind.COMMENT); i = close + 2; state = State.NONE }
                    }
                    State.RAW_STRING -> {
                        val close = line.indexOf("\"\"\"", i)
                        if (close < 0) { add(i, line.length, TokenKind.STRING); i = line.length }
                        else { add(i, close + 3, TokenKind.STRING); i = close + 3; state = State.NONE }
                    }
                    State.NONE -> {
                        val c = line[i]
                        when {
                            line.startsWith("//", i) -> { add(i, line.length, TokenKind.COMMENT); i = line.length }
                            line.startsWith("/*", i) -> {
                                val close = line.indexOf("*/", i + 2)
                                if (close < 0) { add(i, line.length, TokenKind.COMMENT); i = line.length; state = State.BLOCK_COMMENT }
                                else { add(i, close + 2, TokenKind.COMMENT); i = close + 2 }
                            }
                            line.startsWith("\"\"\"", i) -> {
                                val close = line.indexOf("\"\"\"", i + 3)
                                if (close < 0) { add(i, line.length, TokenKind.STRING); i = line.length; state = State.RAW_STRING }
                                else { add(i, close + 3, TokenKind.STRING); i = close + 3 }
                            }
                            c == '"' || c == '\'' -> {
                                var j = i + 1
                                while (j < line.length && line[j] != c) j += if (line[j] == '\\') 2 else 1
                                val end = minOf(j + 1, line.length)
                                add(i, end, TokenKind.STRING); i = end
                            }
                            c.isDigit() -> {
                                var j = i
                                while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
                                add(i, j, TokenKind.NUMBER); i = j
                            }
                            c == '@' -> {
                                var j = i + 1
                                while (j < line.length && line[j].isLetterOrDigit()) j++
                                add(i, j, TokenKind.ANNOTATION); i = j
                            }
                            c.isLetter() || c == '_' -> {
                                var j = i
                                while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                                if (line.substring(i, j) in KEYWORDS) add(i, j, TokenKind.KEYWORD)
                                i = j
                            }
                            else -> i++
                        }
                    }
                }
            }
            tokens
        }
    }
}
