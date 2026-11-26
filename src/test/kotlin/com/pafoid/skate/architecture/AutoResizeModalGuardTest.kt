package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

/**
 * EditorModal is AlwaysAutoResize: a fill-width (0f) child placed beside another item depends on the window width,
 * which depends on that child, so the dialog flickers (EDT-114). Side-by-side children get constant widths.
 */
class AutoResizeModalGuardTest {

    private val root = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    private val zeroWidthChild = Regex("""beginChild\(\s*[^,]+,\s*0f\s*,""")

    @Test
    fun EditorModals_HaveNoFillWidthChildrenBesideOtherItems() {
        val violations = Files.walk(root.resolve("src/main/kotlin/com/pafoid/skate/editor")).use { s ->
            s.filter { it.toString().endsWith(".kt") }.toList()
        }.sorted().filter { it.readText().contains("EditorModal(") }.flatMap { file ->
            findViolations(file.readText()).map { "${root.relativize(file).invariantSeparatorsPathString}:$it" }
        }
        assertTrue(violations.isEmpty(), "Fill-width children in an auto-resize modal:\n" + violations.joinToString("\n"))
    }

    @Test
    fun Guard_FlagsOnlyZeroWidthChildrenAfterSameLine() {
        val src = "ImGui.beginChild(\"a\", 0f, 10f)\nImGui.sameLine()\nImGui.beginChild(\"b\", 0f, 10f)\nImGui.sameLine()\nImGui.beginChild(\"c\", 200f, 0f)\n"
        assertEquals(listOf(3), findViolations(src))
    }

    private fun findViolations(src: String): List<Int> {
        val lines = src.lines()
        return lines.indices.filter { i ->
            zeroWidthChild.containsMatchIn(lines[i]) && lines.take(i).lastOrNull { it.isNotBlank() }?.trim() == "ImGui.sameLine()"
        }.map { it + 1 }
    }
}
