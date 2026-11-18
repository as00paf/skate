package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.ToolWindowShortcuts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-84: where the side-bar buttons go, how they move and are saved, and what a click does. */
class ToolWindowBarsTest {

    @AfterEach
    fun teardown() = ToolWindowStates.reset()

    @Test
    fun EveryToolWindow_ExceptTheViewport_HasAButton() {
        // A new window in View > Windows fails here until it is in ToolWindowShortcuts.WINDOWS (button + shortcut).
        val categorized = WindowCategory.windows
        assertEquals((categorized - MaximizeSession.VIEWPORT_WINDOW).toSet(), ToolWindowShortcuts.WINDOWS.toSet())
        val bars = ToolWindowBars()
        ToolWindowShortcuts.WINDOWS.forEach { assertTrue(bars.sectionOf(it) != null, "$it has no button") }
        assertNull(ToolWindowBars.defaultSection(MaximizeSession.VIEWPORT_WINDOW), "design Q1: no viewport button")
    }

    @Test
    fun DefaultPlacement_FollowsTheDefaultDockZones_AndSplitsTheBottomByCategory() {
        val bars = ToolWindowBars()
        assertEquals(setOf("window.project", "window.hierarchy"), bars.windows(BarSection.LEFT_TOP).toSet())
        assertEquals(setOf("window.properties", "window.audio_inspector"), bars.windows(BarSection.RIGHT_TOP).toSet())
        assertEquals(
            setOf("window.asset_browser", "window.asset_store", "window.video_to_animation", "window.animation_editor", "window.animator", "window.script_editor"),
            bars.windows(BarSection.LEFT_BOTTOM).toSet(),
        )
        assertEquals(
            setOf("window.console", "window.profiler", "window.command_history", "window.render_graph", "window.shell", "window.version_control", "window.help"),
            bars.windows(BarSection.RIGHT_BOTTOM).toSet(),
        )
    }

    @Test
    fun Move_ReordersWithinASection_AndReportsASectionChange() {
        val bars = ToolWindowBars()
        assertFalse(bars.move("window.hierarchy", BarSection.LEFT_TOP, 0))
        assertEquals("window.hierarchy", bars.windows(BarSection.LEFT_TOP).first())
        assertTrue(bars.move("window.console", BarSection.LEFT_TOP, 1))
        assertEquals(listOf("window.hierarchy", "window.console", "window.project"), bars.windows(BarSection.LEFT_TOP))
        assertTrue(bars.move("window.help", BarSection.RIGHT_TOP, Int.MAX_VALUE))
        assertEquals("window.help", bars.windows(BarSection.RIGHT_TOP).last(), "an index past the end appends")
        assertFalse(bars.move("window.unknown", BarSection.LEFT_TOP, 0))
    }

    @Test
    fun Saved_RoundTrips() {
        val bars = ToolWindowBars()
        bars.move("window.console", BarSection.LEFT_TOP, 0)
        bars.move("window.project", BarSection.RIGHT_BOTTOM, 2)
        val back = ToolWindowBars.decode(bars.encode())
        BarSection.entries.forEach { assertEquals(bars.windows(it), back.windows(it), "$it") }
    }

    @Test
    fun Saved_IsMergedWithTheRegisteredWindows() {
        val saved = """
            LEFT_TOP=window.console,window.removed,window.console
            RIGHT_TOP=window.console,window.properties
            NOT_A_SECTION=window.help
        """.trimIndent()
        val bars = ToolWindowBars.decode(saved)
        assertEquals(listOf("window.console", "window.project", "window.hierarchy"), bars.windows(BarSection.LEFT_TOP),
            "unknown dropped, duplicates keep the first place, missing windows appended to their default section")
        assertEquals(listOf("window.properties", "window.audio_inspector"), bars.windows(BarSection.RIGHT_TOP),
            "a window missing from the file goes to its default section")
        assertEquals(BarSection.RIGHT_BOTTOM, bars.sectionOf("window.help"))
        ToolWindowShortcuts.WINDOWS.forEach { assertTrue(bars.sectionOf(it) != null, it) }
    }

    @Test
    fun NothingSaved_IsTheDefault_AndResetRestoresIt() {
        val default = ToolWindowBars()
        val bars = ToolWindowBars.decode(null)
        assertEquals(default.encode(), bars.encode())
        bars.move("window.console", BarSection.LEFT_TOP, 0)
        bars.reset()
        assertEquals(default.encode(), bars.encode())
    }

    @Test
    fun Click_OpensFocusesOrCloses() {
        assertEquals(ToolWindowClick.OPEN, ToolWindowBars.click(ToolWindowState.CLOSED))
        assertEquals(ToolWindowClick.FOCUS, ToolWindowBars.click(ToolWindowState.OPEN))
        assertEquals(ToolWindowClick.CLOSE, ToolWindowBars.click(ToolWindowState.FOCUSED))
    }

    @Test
    fun State_ComesFromTheLastFrame() {
        assertEquals(ToolWindowState.CLOSED, ToolWindowStates.state("window.console", isOpen = false))
        assertEquals(ToolWindowState.OPEN, ToolWindowStates.state("window.console", isOpen = true), "no report yet")
        ToolWindowStates.report("window.console", visible = true, focused = true)
        assertEquals(ToolWindowState.OPEN, ToolWindowStates.state("window.console", isOpen = true), "until the frame ends")
        ToolWindowStates.endFrame()
        assertEquals(ToolWindowState.FOCUSED, ToolWindowStates.state("window.console", isOpen = true))
        ToolWindowStates.report("window.console", visible = false, focused = false)
        ToolWindowStates.endFrame()
        assertEquals(ToolWindowState.OPEN, ToolWindowStates.state("window.console", isOpen = true), "behind another tab")
        assertFalse(ToolWindowStates.inFront("window.console"))
    }

    @Test
    fun Frame_PutsTheDockSpaceBetweenTheBars() {
        val f = ToolWindowBarView.Companion.frame(0f, 40f, 1600f, 900f, barWidth = 33f)
        assertEquals(MaximizeSession.Area(0f, 40f, 33f, 900f), f.left)
        assertEquals(MaximizeSession.Area(33f, 40f, 1534f, 900f), f.dock)
        assertEquals(MaximizeSession.Area(1567f, 40f, 33f, 900f), f.right)
        val hidden = ToolWindowBarView.Companion.frame(0f, 40f, 1600f, 900f, barWidth = 0f)
        assertNull(hidden.left)
        assertEquals(MaximizeSession.Area(0f, 40f, 1600f, 900f), hidden.dock)
    }
}
