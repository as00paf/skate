package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * GPL-08 (QA-04, BUG-011): game/ logs through LoggerService and gets file locations from the engine's path APIs
 * (`Assets.*`, `AssetsPacker.BIN_FILE`, project paths). Console printing and path literals are rejected.
 */
class GameLoggingAndPathsGuardTest {

    @Test
    fun GameLayer_NoConsolePrintingOrHardCodedPaths() {
        val root = Paths.get(System.getProperty("user.dir")).resolve("src/main/kotlin/com/pafoid/skate/game")
        val offenders = Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.toList().flatMap { file -> offenders(root.relativize(file), Files.readString(file)) }
        }
        assertTrue(offenders.isEmpty(), "Use LoggerService and Assets/AssetsPacker/project path APIs in game/: $offenders")
    }

    @Test
    fun GameLayerGuard_FlagsPrintsAndPathLiteralsButNotCommentsOrPlainText() {
        val source = """
            // println("in a comment") and "C:\\temp\\x" are fine here
            /** KDoc mentioning `configs/skater.character.json` is fine too. */
            val name = "Skateboard"
            val ratio = "a/b"
            val path = "C:\\workspace\\Builds"
            val res = "configs/skater.character.json"
            fun f(e: Exception) { println("x"); e.printStackTrace(); System.err.print("y") }
            val home = System.getProperty("user.home")
        """.trimIndent()

        val lines = offenders(Paths.get("Sample.kt"), source).map { it.substringAfter(':').substringBefore(' ').toInt() }

        assertEquals(listOf(5, 6, 7, 7, 7, 7, 8), lines)
    }

    private fun offenders(file: Path, text: String): List<String> {
        val found = mutableListOf<String>()
        var inBlockComment = false
        text.lines().forEachIndexed { index, raw ->
            val code = stripComments(raw, inBlockComment).also { inBlockComment = it.second }.first
            fun flag(what: String) = found.add("$file:${index + 1} $what")
            PRINT_CALLS.filter { it.containsMatchIn(code) }.forEach { flag(it.pattern) }
            STRING_LITERAL.findAll(code).map { it.groupValues[1] }.filter(::looksLikePath).forEach { flag("\"$it\"") }
            if (DIR_PROPERTIES.containsMatchIn(code)) flag("user dir property")
        }
        return found
    }

    /** Code part of [line] (string contents kept) and whether a block comment is still open after it. */
    private fun stripComments(line: String, startsInBlock: Boolean): Pair<String, Boolean> {
        val out = StringBuilder()
        var inBlock = startsInBlock
        var inString = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            val next = line.getOrNull(i + 1)
            when {
                inBlock -> if (c == '*' && next == '/') { inBlock = false; i++ }
                inString -> { out.append(c); if (c == '\\' && next != null) { out.append(next); i++ } else if (c == '"') inString = false }
                c == '/' && next == '/' -> break
                c == '/' && next == '*' -> { inBlock = true; i++ }
                else -> { out.append(c); if (c == '"') inString = true }
            }
            i++
        }
        return out.toString() to inBlock
    }

    /** A drive letter, a backslash separator, or a slash-separated name ending in a file extension. */
    private fun looksLikePath(literal: String): Boolean =
        DRIVE.containsMatchIn(literal) || literal.contains("\\\\") || (literal.contains('/') && FILE_EXTENSION.containsMatchIn(literal))

    private companion object {
        val PRINT_CALLS = listOf(Regex("""\bprintln\("""), Regex("""\bprint\("""), Regex("""\.printStackTrace\("""), Regex("""System\.(out|err)\b"""))
        val STRING_LITERAL = Regex(""""((?:[^"\\]|\\.)*)"""")
        val DRIVE = Regex("""^[A-Za-z]:[\\/]""")
        val FILE_EXTENSION = Regex("""\.[A-Za-z0-9]{1,5}$""")
        val DIR_PROPERTIES = Regex(""""user\.(dir|home)"""")
    }
}
