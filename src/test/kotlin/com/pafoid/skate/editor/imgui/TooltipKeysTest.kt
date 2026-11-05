package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringTables
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-05: tooltip texts exist for every control and property that asks for one. French coverage of the same keys is
 * enforced by StringTablesTest (every key has an entry in the matching `_fr` file).
 */
class TooltipKeysTest {
    private val keys = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS)).stringPropertyNames()
    private val code = File("src/main/kotlin/com/pafoid/skate").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }

    @Test
    fun EveryComponentAndUiInspectorField_HasATooltip() {
        val fields = keys.filter { Regex("""component\.\w+\.\w+|ui\.inspector\.\w+""").matches(it) }
        assertTrue(fields.size > 200)
        assertEquals(emptyList<String>(), fields.filter { Tooltips.keyFor(it) !in keys }.sorted())
    }

    @Test
    fun EveryTippedLabelKey_HasItsDerivedTooltip() {
        val used = tippedKeys(code)
        assertTrue(used.size > 100, "found ${used.size}")
        assertEquals(emptyList<String>(), used.filter { Tooltips.keyFor(it) !in keys }.sorted())
    }

    @Test
    fun EveryExplicitTooltipKey_Exists() {
        val explicit = Regex("""(?:tippedWith\(.*?,\s*|Tooltips\.show(?:IfPresent)?\(\w+(?:\.\w+)?,\s*)"([\w.]+)"""").findAll(code)
            .map { it.groupValues[1] }.toSet() +
            Regex(""""(lbl\.animator\.(?:pause|play)\.tooltip)"""").findAll(code).map { it.groupValues[1] }
        assertTrue(explicit.isNotEmpty())
        assertEquals(emptyList<String>(), explicit.filter { it !in keys }.sorted())
    }

    /** Last argument (a string literal) of every `.tipped(` call, found with a bracket scanner. */
    private fun tippedKeys(src: String): Set<String> {
        val out = HashSet<String>()
        var at = src.indexOf(".tipped(")
        while (at >= 0) {
            var i = at + ".tipped(".length
            var depth = 0
            var lastLiteral: String? = null
            var inString = false
            val sb = StringBuilder()
            loop@ while (i < src.length) {
                val c = src[i]
                if (inString) {
                    when (c) {
                        '\\' -> { sb.append(c); i++; if (i < src.length) sb.append(src[i]) }
                        '"' -> { inString = false; if (depth == 0) lastLiteral = sb.toString() }
                        else -> sb.append(c)
                    }
                } else when (c) {
                    '"' -> { inString = true; sb.setLength(0) }
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> { if (depth == 0) break@loop; depth-- }
                    ',' -> if (depth == 0) lastLiteral = null
                }
                i++
            }
            lastLiteral?.takeIf { Regex("""[\w.]+""").matches(it) }?.let(out::add)
            at = src.indexOf(".tipped(", i)
        }
        return out
    }
}
