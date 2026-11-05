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
 * EDT-38: ImGui identifies windows, tabs, tree nodes, headers, menus, popups, child windows, tables and combos by
 * their label. A label translated by the StringManager changes with the editor language, so these calls must give
 * a stable ID: `stringManager.label(key)` / `EditorWindow.title(...)` (`text###key`) or an explicit `###id`.
 * `text##id` is not enough: ImGui still hashes the whole label, translated text included.
 */
class ImGuiStableIdGuardTest {

    private val root: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    private val scanned = listOf(
        "src/main/kotlin/com/pafoid/skate/editor",
        "src/main/kotlin/com/pafoid/skate/app",
    )

    @Test
    fun ImGuiIdCalls_WithTranslatedLabels_HaveStableIds() {
        val violations = scanned.flatMap { dir ->
            Files.walk(root.resolve(dir)).use { s -> s.filter { it.isRegularFile() && it.toString().endsWith(".kt") }.toList() }
        }.sorted().flatMap { file ->
            val rel = root.relativize(file).invariantSeparatorsPathString
            findViolations(file.readText()).map { (line, call) -> "$rel:$line -> $call" }
        }
        assertTrue(violations.isEmpty(), "ImGui IDs built from translated labels (use stringManager.label(key) or '###id'):\n" +
            violations.joinToString("\n"))
    }

    @Test
    fun Guard_FlagsTranslatedLabelsAndAcceptsStableOnes() {
        val src = """
            import imgui.internal.ImGui.beginMenu
            fun a() {
                ImGui.begin(stringManager.getString("window.console"), isOpen)
                beginMenu(stringManager.getString("menu.file"))
                ImGui.collapsingHeader(
                    "${'$'}{Icons.GEAR} ${'$'}{s("lbl.x")}"
                )
                ImGui.begin(stringManager.label("window.console"), isOpen)
                ImGui.beginTabItem("${'$'}{strings.getString("tab.a")}###tab.a")
                ImGui.openPopup(title(stringManager))
                ImGui.treeNodeEx("##${'$'}{obj.uId}", flags)
                ImGui.begin("##StatusBar", flags)
                renderer.begin(stringManager.getString("not.imgui"))
                ImGui.collapsingHeader("${'$'}{s("lbl.group")}##group_a")
            }
        """.trimIndent()
        assertEquals(listOf(3, 4, 5, 14), findViolations(src).map { it.first })
    }

    companion object {
        private val CALLS = listOf(
            "begin", "beginMenu", "beginTabBar", "beginTabItem", "beginPopup", "beginPopupModal",
            "beginPopupContextItem", "beginPopupContextWindow", "beginPopupContextVoid", "openPopup",
            "treeNode", "treeNodeEx", "collapsingHeader", "beginChild", "beginTable", "beginCombo", "combo",
        )
        private val TRANSLATED = Regex("""getString\s*\(|\bs\s*\(\s*"|\btr\s*\(""")

        /** (1-based line, call text) for each guarded call whose first argument is translated without a stable ID. */
        fun findViolations(source: String): List<Pair<Int, String>> {
            val bare = CALLS.filter { Regex("""import\s+imgui(\.internal)?\.ImGui\.$it\b""").containsMatchIn(source) }.toSet()
            val call = Regex("""(?<![\w.])(ImGui\.)?(${CALLS.joinToString("|")})\s*\(""")
            val out = ArrayList<Pair<Int, String>>()
            for (m in call.findAll(source)) {
                val name = m.groupValues[2]
                if (m.groupValues[1].isEmpty() && name !in bare) continue
                if (isDeclarationOrComment(source, m.range.first)) continue
                val arg = firstArgument(source, m.range.last + 1) ?: continue
                if (!TRANSLATED.containsMatchIn(arg)) continue
                if (arg.contains("###") || Regex("""\b(label|title|stable)\s*\(""").containsMatchIn(arg)) continue
                val line = source.substring(0, m.range.first).count { it == '\n' } + 1
                out += line to "${m.value}${arg.lines().joinToString(" ") { it.trim() }}"
            }
            return out
        }

        private fun isDeclarationOrComment(src: String, at: Int): Boolean {
            val lineStart = src.lastIndexOf('\n', at - 1) + 1
            val before = src.substring(lineStart, at).trim()
            return before.startsWith("//") || before.startsWith("*") || before.endsWith("fun")
        }

        /** Text of the first top-level argument starting at [from] (just after '('), aware of strings and templates. */
        private fun firstArgument(src: String, from: Int): String? {
            var depth = 0
            var i = from
            var inString = false
            var templateDepth = 0
            while (i < src.length) {
                val c = src[i]
                if (inString) {
                    when {
                        c == '\\' -> i++
                        c == '$' && i + 1 < src.length && src[i + 1] == '{' -> { templateDepth++; inString = false; i++ }
                        c == '"' -> inString = false
                    }
                } else when (c) {
                    '"' -> inString = true
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> {
                        if (c == '}' && templateDepth > 0 && depth == 0) { templateDepth--; inString = true }
                        else if (depth == 0) return src.substring(from, i)
                        else depth--
                    }
                    ',' -> if (depth == 0 && templateDepth == 0) return src.substring(from, i)
                }
                i++
            }
            return null
        }
    }
}
