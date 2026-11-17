package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * EDT-57: the Show tooltips setting is honoured by every tooltip, so editor code never calls `ImGui.setTooltip`
 * itself. Help on controls goes through `Tooltips.show` / `tipped` / `Tooltips.text` (hidden when the setting is off);
 * field explanations and data through `Tooltips.showIfPresent` / `Tooltips.always`.
 *
 * [ALLOW_LIST] is empty since EDT-65 and stays empty. Hover checks that are not ImGui items (canvas hit-tests) use
 * `Tooltips.set` with a kind.
 */
class TooltipToggleGuardTest {
    private val root: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()

    private val offenders: Map<String, Int> by lazy {
        listOf("src/main/kotlin/com/pafoid/skate/editor", "src/main/kotlin/com/pafoid/skate/app").flatMap { dir ->
            Files.walk(root.resolve(dir)).use { s -> s.filter { it.isRegularFile() && it.name.endsWith(".kt") }.toList() }
        }.filter { it.name != "Tooltips.kt" }
            .associate { it.name to rawCalls(it.readText()) }
            .filterValues { it > 0 }
    }

    @Test
    fun Tooltips_GoThroughTheToggle() {
        val bad = offenders.keys - ALLOW_LIST
        assertTrue(bad.isEmpty(), "Direct ImGui.setTooltip calls bypass Show tooltips (EDT-57); use Tooltips.text for help " +
            "on controls or Tooltips.always for field explanations and data: $bad")
    }

    @Test
    fun AllowList_OnlyHoldsFilesThatStillNeedWork() {
        val done = ALLOW_LIST - offenders.keys
        assertTrue(done.isEmpty(), "These files no longer call ImGui.setTooltip; remove them from ALLOW_LIST: $done")
    }

    @Test
    fun AllowList_IsEmpty() {
        assertTrue(ALLOW_LIST.isEmpty(), "EDT-65: every tooltip honours Show tooltips; do not add files: $ALLOW_LIST")
    }

    @Test
    fun Guard_CountsDirectCallsOnly() {
        val src = """
            if (ImGui.isItemHovered()) ImGui.setTooltip("a")
            Tooltips.text("b")
            Tooltips.set(Tooltips.Kind.HELP, "c")
            // ImGui.setTooltip("in a comment")
            setTooltip(x)
        """.trimIndent()
        assertEquals(2, rawCalls(src))
    }

    companion object {
        /** Files allowed to call `ImGui.setTooltip` directly. EDT-65 converted the last ones; nobody adds any. */
        val ALLOW_LIST = emptySet<String>()

        private val CALL = Regex("""(?<![\w.])(ImGui\.)?setTooltip\s*\(""")

        fun rawCalls(source: String): Int = source.lines()
            .filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }
            .sumOf { line -> CALL.findAll(line).count() }
    }
}
