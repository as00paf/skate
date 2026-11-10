package com.pafoid.skate.editor.imgui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-52A: the logic behind the window header, tab menu, help popover, footer and Reset position. */
class WindowStandardPartsTest {

    private class TestWindow(name: String) : EditorWindow(name) {
        override fun imgui() {}
    }

    @Test
    fun TabMenu_WindowsGetResetPosition_DialogsDoNot() {
        assertEquals(listOf(TabMenuItem.HELP, TabMenuItem.RESET_POSITION, TabMenuItem.CLOSE), TabMenuItem.forWindow(isDialog = false))
        assertEquals(listOf(TabMenuItem.HELP, TabMenuItem.CLOSE), TabMenuItem.forWindow(isDialog = true))
    }

    @Test
    fun TabMenu_ItemsActOnTheWindow() {
        val window = TestWindow("window.console").also { it.isOpen.set(true) }
        WindowHeader.run(TabMenuItem.RESET_POSITION, window)
        assertTrue(window.resetPositionRequested)
        WindowHeader.run(TabMenuItem.CLOSE, window)
        assertFalse(window.isOpen.get())
    }

    @Test
    fun Help_IdAndKeysComeFromTheWindowName() {
        assertEquals("console", TestWindow("window.console").helpId)
        assertEquals(listOf("help.console.what", "help.console.how", "help.console.related"), HelpPopover.keysOf("console"))
    }

    @Test
    fun Help_WhatAndHowAlwaysShow_RelatedOnlyWhenWritten() {
        val written = mapOf("help.console.what" to "Logs.")
        val sections = HelpPopover.sections("console") { written[it] }
        assertEquals(listOf("help.section.what", "help.section.how"), sections.map { it.titleKey })
        assertEquals(listOf("Logs.", null), sections.map { it.text })

        val withRelated = HelpPopover.sections("console") { "x" }
        assertEquals("help.section.related", withRelated.last().titleKey)
    }

    @Test
    fun Footer_OrderIsSecondaryCancelApplyMain() {
        val ok = FooterButton("btn.ok", FooterButton.Role.PRIMARY)
        val cancel = FooterButton("btn.cancel", FooterButton.Role.CANCEL)
        val apply = FooterButton("btn.apply", FooterButton.Role.APPLY)
        val copy = FooterButton("error.dialog.copy", FooterButton.Role.SECONDARY, closes = false)
        assertEquals(listOf(copy, cancel, apply, ok), DialogFooter.order(listOf(ok, apply, cancel, copy)))
    }

    @Test
    fun Footer_EscapeRunsCancelOrTheOnlyClosingButton() {
        val cancel = FooterButton("btn.cancel", FooterButton.Role.CANCEL)
        val ok = FooterButton("btn.ok", FooterButton.Role.PRIMARY)
        val copy = FooterButton("error.dialog.copy", FooterButton.Role.SECONDARY, closes = false)
        assertSame(cancel, DialogFooter.escapeTarget(listOf(ok, cancel)))
        assertSame(ok, DialogFooter.escapeTarget(listOf(copy, ok)))
        assertNull(DialogFooter.escapeTarget(listOf(ok, FooterButton("btn.apply", FooterButton.Role.APPLY))))
    }

    @Test
    fun Footer_EnterRunsTheEnabledMainAction() {
        val delete = FooterButton("btn.delete", FooterButton.Role.DANGER)
        val cancel = FooterButton("btn.cancel", FooterButton.Role.CANCEL)
        assertSame(delete, DialogFooter.enterTarget(listOf(cancel, delete)))
        assertNull(DialogFooter.enterTarget(listOf(cancel, FooterButton("btn.ok", FooterButton.Role.PRIMARY, enabled = false))))
        assertNull(DialogFooter.enterTarget(listOf(cancel)))
    }

    @Test
    fun ResetPosition_DocksWithAWindowOfTheSameZone() {
        val docks = DefaultDockLayout.DockMemory()
        docks.record("window.console", 11)
        docks.record("window.profiler", 11)
        docks.record("window.properties", 22)
        // Console was dragged out to float.
        docks.record("window.console", 0)
        assertEquals(11, docks.resetTarget("window.console"))
        assertEquals(null, docks.resetTarget("window.project_settings"))
    }

    @Test
    fun ResetPosition_FallsBackToTheWindowsFirstDock() {
        val docks = DefaultDockLayout.DockMemory()
        docks.record("window.hierarchy", 5)
        docks.record("window.hierarchy", 0)
        assertEquals(5, docks.resetTarget("window.hierarchy"))
    }

    @Test
    fun DefaultLayout_HasNoStaleWindows() {
        assertNull(DefaultDockLayout.zoneOf("window.environment"))
        assertNull(DefaultDockLayout.zoneOf("window.physics_tuner"))
    }

    @Test
    fun Footer_HeightIsSpacingSeparatorSpacingAndOneButtonRow() {
        // 26 (button row with spacing) + 4 × 4 (item spacings) + 1 (separator) = 43.
        assertEquals(43f, DialogFooter.height(frameHeightWithSpacing = 26f, itemSpacingY = 4f))
    }
}
