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
 * EDT-05: every button and menu item with a translated or icon label has a tooltip, either by wrapping the call in
 * `strings.tipped(...)` / `tippedWith(...)` (see editor/imgui/Tooltips.kt) or by an `isItemHovered` -> `setTooltip`
 * (or a local `tip(...)` helper) right after the statement. Labels that are data (object names, recent projects,
 * file names) and the generic dialog buttons (OK, Cancel, Apply, Close) need none.
 */
class TooltipGuardTest {

    private val root: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()

    @Test
    fun ButtonsAndMenuItems_HaveTooltips() {
        val violations = listOf("src/main/kotlin/com/pafoid/skate/editor", "src/main/kotlin/com/pafoid/skate/app").flatMap { dir ->
            Files.walk(root.resolve(dir)).use { s -> s.filter { it.isRegularFile() && it.toString().endsWith(".kt") }.toList() }
        }.sorted().flatMap { file ->
            val rel = root.relativize(file).invariantSeparatorsPathString
            findViolations(file.readText()).map { (line, arg) -> "$rel:$line -> $arg" }
        }
        assertTrue(violations.isEmpty(), "Buttons/menu items without a tooltip (wrap in strings.tipped(..., labelKey) and add " +
            "<labelKey>.tooltip EN+FR):\n" + violations.joinToString("\n"))
    }

    @Test
    fun Guard_FlagsMissingTooltipsOnly() {
        val src = """
            import imgui.internal.ImGui.menuItem
            fun a() {
                if (ImGui.button(strings.getString("btn.export.start"))) start()
                if (strings.tipped(ImGui.button(strings.label("btn.x")), "btn.x")) go()
                if (ImGui.button(Icons.PLAY)) {
                    play()
                    more()
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString("tooltip.play"))
                if (menuItem(stringManager.getString("menu.file.save"))) save()
                if (ImGui.menuItem(project.name)) open(project)
                if (ImGui.button(stringManager.getString("btn.ok"))) close()
                if (ImGui.button("${'$'}{Icons.TRASH}##del")) delete()
                tip("delete")
            }
        """.trimIndent()
        assertEquals(listOf(3, 10), findViolations(src).map { it.first })
    }

    companion object {
        private val CALLS = listOf("button", "smallButton", "menuItem")
        private val TRANSLATED = Regex("""getString\s*\(|\blabel\s*\(|\btitle\s*\(|\bs\s*\(\s*"|\btr\s*\(|Icons\.""")
        private val GENERIC = Regex(""""btn\.(ok|cancel|apply|close|yes|no)"""")
        private val CONTROL = Regex("""(?<![\w.])(ImGui\.)?(button|smallButton|menuItem)\s*\(""")
        private val SKIP = Regex("""^(}|if \(!?\w+\) \{|ImGui\.pop\w*\(.*\)|pop\w*\(.*\)|ImGui\.endDisabled\(\)|if \(\w+\) ImGui\.pop\w*\(.*\))$""")
        private val TOOLTIP = Regex("""setTooltip\s*\(|\btip\s*\(|\btooltip\s*\(|isItemHovered|Tooltips\.show\s*\(|\btipped(With)?\s*\(""")

        fun findViolations(source: String): List<Pair<Int, String>> {
            val bare = CALLS.filter { Regex("""import\s+imgui(\.internal)?\.ImGui\.$it\b""").containsMatchIn(source) }.toSet()
            val call = Regex("""(?<![\w.])(ImGui\.)?(${CALLS.joinToString("|")})\s*\(""")
            val out = ArrayList<Pair<Int, String>>()
            for (m in call.findAll(source)) {
                if (m.groupValues[1].isEmpty() && m.groupValues[2] !in bare) continue
                val lineStart = source.lastIndexOf('\n', m.range.first - 1) + 1
                val before = source.substring(lineStart, m.range.first)
                if (before.trim().let { it.startsWith("//") || it.startsWith("*") || it.endsWith("fun") }) continue
                if (Regex("""\btipped(With)?\s*\(""").containsMatchIn(before)) continue
                // Wrapper opened on the previous line: `strings.tippedWith(\n    ImGui.button(...), ...)`.
                if (Regex("""\btipped(With)?\(\s*$""").containsMatchIn(source.substring(maxOf(0, m.range.first - 120), m.range.first))) continue
                val callEnd = closingParen(source, m.range.last + 1) ?: continue
                val args = source.substring(m.range.last + 1, callEnd)
                val first = firstArgument(args)
                if (!TRANSLATED.containsMatchIn(first) || GENERIC.containsMatchIn(first)) continue
                if (hasTooltipAfter(source, callEnd)) continue
                out += (source.substring(0, m.range.first).count { it == '\n' } + 1) to first.lines().joinToString(" ") { it.trim() }
            }
            return out
        }

        /** True when a tooltip call follows the statement that contains the control (after its `if` body, if any). */
        private fun hasTooltipAfter(src: String, callEnd: Int): Boolean {
            var i = callEnd + 1
            // Leave the enclosing condition: skip closing parens, operators and spaces on this line.
            while (i < src.length && src[i] != '{' && src[i] != '\n') i++
            if (i < src.length && src[i] == '{') i = closingBrace(src, i + 1) ?: return false
            val sameLine = src.substring(callEnd, src.indexOf('\n', callEnd).let { if (it < 0) src.length else it })
            if (TOOLTIP.containsMatchIn(sameLine) && !CONTROL.containsMatchIn(sameLine.drop(1))) return true
            // The first statement after the control (style pops / closing braces aside) must be its tooltip.
            val rest = src.substring(minOf(i, src.length)).lines().let { if (src.getOrNull(i) == '\n') it.drop(1) else it.drop(1) }
            for (raw in rest.take(6)) {
                val line = raw.trim()
                if (line.isEmpty() || SKIP.matches(line)) continue
                return TOOLTIP.containsMatchIn(line) && !CONTROL.containsMatchIn(line)
            }
            return false
        }

        private fun closingParen(src: String, from: Int): Int? = closing(src, from, '(', ')')
        private fun closingBrace(src: String, from: Int): Int? = closing(src, from, '{', '}')

        /** Index of the bracket closing the one opened just before [from], skipping string literals and templates. */
        private fun closing(src: String, from: Int, open: Char, close: Char): Int? {
            var depth = 0
            var i = from
            while (i < src.length) {
                when (val c = src[i]) {
                    '"' -> { i = endOfLiteral(src, i + 1); continue }
                    open -> depth++
                    close -> if (depth == 0) return i else depth--
                    else -> if (c == '/' && src.startsWith("//", i)) { i = src.indexOf('\n', i).let { if (it < 0) src.length else it }; continue }
                }
                i++
            }
            return null
        }

        private fun endOfLiteral(src: String, from: Int): Int {
            var i = from
            while (i < src.length) {
                when {
                    src[i] == '\\' -> i += 2
                    src[i] == '"' -> return i + 1
                    src[i] == '$' && i + 1 < src.length && src[i + 1] == '{' -> i = (closingBrace(src, i + 2) ?: return src.length) + 1
                    else -> i++
                }
            }
            return i
        }

        private fun firstArgument(args: String): String {
            var depth = 0
            var i = 0
            while (i < args.length) {
                when (args[i]) {
                    '"' -> { i = endOfLiteral(args, i + 1); continue }
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    ',' -> if (depth == 0) return args.substring(0, i)
                }
                i++
            }
            return args
        }
    }
}
