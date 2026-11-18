package com.pafoid.skate.editor.imgui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-84: a button (or shortcut) opens, focuses or closes its window; a moved button is saved and re-docks. */
class ToolWindowBarsControllerTest {

    private class W(name: String) : EditorWindow(name) {
        override fun imgui() {}
    }

    private val windows = listOf("window.console", "window.properties", "window.hierarchy", "window.project").associateWith { W(it) }
    private val shown = mutableListOf<String>()
    private val focused = mutableListOf<String>()
    private var saved: String? = null
    private val docks = DefaultDockLayout.DockMemory()

    private fun controller(load: String? = null) = ToolWindowBarsController(
        windowOf = { windows[it] }, load = { load }, save = { saved = it },
        show = { shown += it; windows[it]?.isOpen?.set(true) }, focus = { focused += it }, docks = docks,
    )

    @AfterEach
    fun teardown() = ToolWindowStates.reset()

    private fun frame(window: String, visible: Boolean, focus: Boolean) {
        ToolWindowStates.report(window, visible, focus)
        ToolWindowStates.endFrame()
    }

    @Test
    fun AClosedWindow_Opens_AndComesToTheFront() {
        controller().toggle("window.console")
        assertEquals(listOf("window.console"), shown)
        assertEquals(listOf("window.console"), focused)
        assertTrue(windows.getValue("window.console").isOpen.get())
    }

    @Test
    fun AnOpenWindowBehindATab_ComesToTheFront() {
        val c = controller()
        windows.getValue("window.console").isOpen.set(true)
        frame("window.console", visible = false, focus = false)
        c.toggle("window.console")
        assertEquals(listOf("window.console"), focused)
        assertTrue(shown.isEmpty())
        assertTrue(windows.getValue("window.console").isOpen.get())
    }

    @Test
    fun TheFocusedWindow_Closes() {
        val c = controller()
        windows.getValue("window.console").isOpen.set(true)
        frame("window.console", visible = true, focus = true)
        c.toggle("window.console")
        assertFalse(windows.getValue("window.console").isOpen.get())
        assertTrue(focused.isEmpty())
    }

    @Test
    fun MovingAButton_SavesIt_AndReDocksTheWindowNextToItsNewNeighbours() {
        val c = controller()
        docks.record("window.properties", 42)
        c.move("window.console", BarSection.RIGHT_TOP, 0)
        assertEquals(BarSection.RIGHT_TOP, ToolWindowBars.decode(saved).sectionOf("window.console"))
        assertEquals(42, windows.getValue("window.console").dockRequest)
    }

    @Test
    fun MovingToAnEmptySide_DocksWithTheSidesDefaultZone() {
        val c = controller()
        docks.record("window.hierarchy", 7) // LEFT zone, but its button stays on LEFT_TOP
        c.move("window.hierarchy", BarSection.RIGHT_BOTTOM, 0)
        c.move("window.project", BarSection.RIGHT_BOTTOM, 0)
        // Project's new neighbours: Hierarchy (docked at 7) is now in RIGHT_BOTTOM.
        assertEquals(7, windows.getValue("window.project").dockRequest)

        val fresh = controller()
        fresh.move("window.console", BarSection.LEFT_TOP, 0)
        assertEquals(7, windows.getValue("window.console").dockRequest, "a LEFT-zone window is docked at 7")
    }

    @Test
    fun ReorderingWithinASection_DoesNotReDock() {
        val c = controller()
        docks.record("window.project", 3)
        c.move("window.hierarchy", BarSection.LEFT_TOP, 0)
        assertNull(windows.getValue("window.hierarchy").dockRequest)
        assertTrue(saved != null, "still saved")
    }

    @Test
    fun TheSavedPlacement_IsLoaded() {
        val c = controller(load = "LEFT_TOP=window.console")
        assertEquals(BarSection.LEFT_TOP, c.bars.sectionOf("window.console"))
        c.reset()
        assertEquals(BarSection.RIGHT_BOTTOM, c.bars.sectionOf("window.console"))
    }
}
