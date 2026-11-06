package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * EDT-06: editor UI text comes from the StringManager. Fails when an ImGui call (or an MImGui text helper) gets a
 * string literal whose visible part (before `##`, without `${...}` templates and format specifiers) contains words.
 * Unit suffixes such as `ms` are allowed. EDT-50 adds table column names and input hints (the second argument of
 * `inputTextWithHint`).
 */
class EditorHardcodedLabelGuardTest {

    private val root: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()

    @Test
    fun EditorUi_HasNoHardcodedImGuiLabels() {
        val violations = listOf("src/main/kotlin/com/pafoid/skate/editor", "src/main/kotlin/com/pafoid/skate/app").flatMap { dir ->
            Files.walk(root.resolve(dir)).use { s -> s.filter { it.isRegularFile() && it.toString().endsWith(".kt") }.toList() }
        }.sorted().flatMap { file ->
            val rel = root.relativize(file).invariantSeparatorsPathString
            findViolations(file.readText()).map { (line, text) -> "$rel:$line -> \"$text\"" }
        }
        assertTrue(violations.isEmpty(), "Hardcoded UI text (add a key to assets/strings/editor/<area>.properties + _fr):\n" +
            violations.joinToString("\n"))
    }

    @Test
    fun Guard_FlagsWordsAndAcceptsKeysIdsIconsAndUnits() {
        val src = """
            ImGui.button("Open Project")
            ImGui.text("${'$'}{Icons.CHECK} Valid")
            MImGui.warningText("(${'$'}n disabled)")
            ImGui.button(stringManager.getString("btn.ok"))
            ImGui.checkbox("##enabled", flag)
            ImGui.text("${'$'}{s("lbl.x")}: %.2fms")
            ImGui.button("${'$'}{Icons.PLUS}##add")
            ImGui.text("${'$'}{strings.getString("a")} (${'$'}{strings.getString("b")})")
            ImGui.text("X: %.2f")
            ImGui.tableSetupColumn("Name")
            ImGui.inputTextWithHint("##search", "Search...", value)
            ImGui.tableSetupColumn("##Label")
            ImGui.inputTextWithHint("##search", "${'$'}{s("lbl.search")}...", value)
        """.trimIndent()
        assertEquals(listOf(1, 2, 3, 10, 11), findViolations(src).map { it.first })
    }

    companion object {
        private val CALLS = listOf(
            "text", "textDisabled", "textWrapped", "textColored", "bulletText", "labelText", "button", "smallButton",
            "checkbox", "radioButton", "menuItem", "selectable", "beginMenu", "beginTabItem", "treeNode", "treeNodeEx",
            "collapsingHeader", "combo", "beginCombo", "sliderFloat", "sliderInt", "dragFloat", "dragFloat2", "dragFloat3",
            "dragInt", "inputText", "inputInt", "inputFloat", "colorEdit3", "colorEdit4", "setTooltip", "begin",
            "beginPopupModal", "warningText", "coloredText", "errorText", "successText", "propertyRow", "separatorText",
            "tableSetupColumn", "inputTextMultiline", "sliderFloat2", "sliderFloat3", "sliderFloat4", "dragFloat4", "dragInt2",
            "inputFloat2", "inputFloat3", "colorPicker3", "colorPicker4",
        )
        private val CALL = Regex(
            """(?<![\w.])(?:ImGui\.|MImGui\.)?(?:(?:${CALLS.joinToString("|")})\s*\(\s*(?:label\s*=\s*)?|inputTextWithHint\s*\(\s*"(?:[^"\\]|\\.)*"\s*,\s*)"""",
        )
        private val ALLOWED_WORDS = setOf("ms", "px", "fps", "FPS")

        fun findViolations(source: String): List<Pair<Int, String>> {
            val out = ArrayList<Pair<Int, String>>()
            for (m in CALL.findAll(source)) {
                val lineStart = source.lastIndexOf('\n', m.range.first - 1) + 1
                val before = source.substring(lineStart, m.range.first).trim()
                if (before.startsWith("//") || before.startsWith("*") || before.endsWith("fun")) continue
                val literal = literalVisibleText(source, m.range.last + 1)
                val visible = literal.substringBefore("##").replace(Regex("""%[-+ #0-9.]*[a-zA-Z]"""), " ")
                val words = Regex("""[A-Za-z]{2,}""").findAll(visible).map { it.value }.filter { it !in ALLOWED_WORDS }
                if (words.any()) out += (source.substring(0, m.range.first).count { it == '\n' } + 1) to literal.replace('\u0000', '…')
            }
            return out
        }

        /** Text of the literal opening just before [from], with every template expression replaced by NUL. */
        private fun literalVisibleText(src: String, from: Int): String {
            val sb = StringBuilder()
            var i = from
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '\\' -> { sb.append(src, i, minOf(i + 2, src.length)); i += 2 }
                    c == '"' -> return sb.toString()
                    c == '$' && i + 1 < src.length && src[i + 1] == '{' -> { i = endOfTemplate(src, i + 2); sb.append('\u0000') }
                    c == '$' && i + 1 < src.length && (src[i + 1].isLetter() || src[i + 1] == '_') -> {
                        i++
                        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                        sb.append('\u0000')
                    }
                    else -> { sb.append(c); i++ }
                }
            }
            return sb.toString()
        }

        /** Index just after the closing quote of the literal whose body starts at [from]. */
        private fun endOfLiteral(src: String, from: Int): Int {
            var i = from
            while (i < src.length) {
                when {
                    src[i] == '\\' -> i += 2
                    src[i] == '"' -> return i + 1
                    src[i] == '$' && i + 1 < src.length && src[i + 1] == '{' -> i = endOfTemplate(src, i + 2)
                    else -> i++
                }
            }
            return i
        }

        /** Index just after the `}` closing a template whose body starts at [from]; nested literals are skipped. */
        private fun endOfTemplate(src: String, from: Int): Int {
            var depth = 1
            var i = from
            while (i < src.length) {
                when (src[i]) {
                    '"' -> { i = endOfLiteral(src, i + 1); continue }
                    '{' -> depth++
                    '}' -> if (--depth == 0) return i + 1
                }
                i++
            }
            return i
        }
    }
}
