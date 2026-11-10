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
 * [ALLOW_LIST] holds the files still being reworked by other EDT-52 chunks; each chunk converts its files and removes
 * them. Nobody adds any.
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
    fun Guard_CountsDirectCallsOnly() {
        val src = """
            if (ImGui.isItemHovered()) ImGui.setTooltip("a")
            Tooltips.text("b")
            // ImGui.setTooltip("in a comment")
            setTooltip(x)
        """.trimIndent()
        assertEquals(2, rawCalls(src))
    }

    companion object {
        /** Files still reworked by other EDT-52 chunks; each removes its own when converted. */
        val ALLOW_LIST = setOf(
            // C: project & assets
            "AssetBrowserTab.kt", "AnimationsTab.kt", "SoundsTab.kt", "TexturesTab.kt",
            // D: debug
            "ConsoleWindow.kt", "ProfilerWindow.kt", "CommandHistoryWindow.kt", "ShellWindow.kt",
            // G: menus
            "EditorMenuBar.kt", "WindowControlsRenderer.kt",
            // H: domain windows
            "AudioMixerPanel.kt", "ScriptEditorWindow.kt",
            // I: input testing move (the window is deleted)
            "InputTestingWindow.kt",
            // EDT-54: Render Graph rewrite
            "RenderGraphWindow.kt",
        )

        private val CALL = Regex("""(?<![\w.])(ImGui\.)?setTooltip\s*\(""")

        fun rawCalls(source: String): Int = source.lines()
            .filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }
            .sumOf { line -> CALL.findAll(line).count() }
    }
}
