package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.DefaultDockLayout.Zone
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * EDT-84: real ImGui frames (headless, docking on) with the bars drawn around the dock space as `ImGuiLayer` does.
 * The buttons sit in their bars, the dock space and EDT-88's host area are narrower by the bars, and mouse clicks on a
 * button open, focus and close its window.
 */
class ToolWindowBarViewTest {

    private val strings: StringManager = mockk(relaxed = true)

    private class Probe(name: String, private val strings: StringManager) : EditorWindow(name) {
        var visible = false
        override fun imgui() {
            visible = beginWindow(strings)
            ImGui.text(name)
            ImGui.end()
        }
    }

    private val zones = linkedMapOf(
        "window.hierarchy" to Zone.LEFT,
        "window.game_viewport" to Zone.CENTER,
        "window.properties" to Zone.RIGHT,
        "window.console" to Zone.BOTTOM,
        "window.profiler" to Zone.BOTTOM,
    )
    private val probes = zones.keys.associateWith { Probe(it, strings) }
    private val pendingFocus = mutableListOf<String>()
    private val controller = ToolWindowBarsController(
        windowOf = { probes[it] }, load = { null }, save = {},
        show = { probes[it]?.isOpen?.set(true) }, focus = { pendingFocus += it },
    )
    private val view = ToolWindowBarView(strings, controller) { probes[it] }
    private val dock = HeadlessDock(zones.mapKeys { (name, _) -> probes.getValue(name).title(strings) })
    private lateinit var layout: ToolWindowBarView.Companion.Frame

    @BeforeEach
    fun setup() {
        dock.create()
        probes.values.forEach { it.isOpen.set(it.name != "window.console") }
    }

    @AfterEach
    fun teardown() {
        ToolWindowStates.reset()
        MaximizeSession.reset()
        dock.destroy()
    }

    private fun frame() {
        dock.frame(
            beforeDockSpace = {
                layout = ToolWindowBarView.frame(
                    ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY(), ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(),
                    ToolWindowBarView.width(),
                )
                layout.left?.let { view.render(true, it.x, it.y, it.height) }
                layout.right?.let { view.render(false, it.x, it.y, it.height) }
                MaximizeSession.hostArea = layout.dock
                ImGui.setCursorScreenPos(layout.dock.x, layout.dock.y)
            },
            dockSpaceSize = { layout.dock.width to layout.dock.height },
            windows = { probes.values.filter { it.isOpen.get() }.forEach { it.imgui() } },
            afterWindows = {
                pendingFocus.forEach { name -> ImGui.setWindowFocus(probes.getValue(name).title(strings)) }
                pendingFocus.clear()
                ToolWindowStates.endFrame()
            },
        )
    }

    private fun click(window: String) {
        val b = view.drawn.getValue(window)
        val io = ImGui.getIO()
        io.addMousePosEvent(b.x + b.width / 2f, b.y + b.height / 2f)
        frame()
        io.addMouseButtonEvent(0, true)
        frame()
        io.addMouseButtonEvent(0, false)
        frame()
        frame()
    }

    @Test
    fun ButtonsSitInTheirBars_AndTheDockSpaceIsBetweenThem() {
        repeat(3) { frame() }
        val left = layout.left!!
        val right = layout.right!!
        fun inside(a: MaximizeSession.Area, b: MaximizeSession.Area) =
            a.x >= b.x && a.x + a.width <= b.x + b.width && a.y >= b.y && a.y + a.height <= b.y + b.height
        assertTrue(inside(view.drawn.getValue("window.hierarchy"), left), "Hierarchy: left top")
        assertTrue(inside(view.drawn.getValue("window.properties"), right), "Properties: right top")
        assertTrue(inside(view.drawn.getValue("window.console"), right), "Console: right bottom")
        assertTrue(view.drawn.getValue("window.console").y > view.drawn.getValue("window.properties").y + 100f, "bottom section")
        assertFalse("window.game_viewport" in view.drawn, "no viewport button")
        assertEquals(layout.dock, MaximizeSession.hostArea)
        val node = dock.central()
        assertTrue(node.posX >= left.x + left.width - 0.5f, "the dock space starts after the left bar")
    }

    @Test
    fun Clicks_OpenFocusAndClose() {
        repeat(3) { frame() }
        val console = probes.getValue("window.console")
        click("window.console")
        assertTrue(console.isOpen.get(), "opened")
        assertTrue(console.visible, "in front")
        assertEquals(ToolWindowState.FOCUSED, controller.state("window.console"))

        click("window.console")
        assertFalse(console.isOpen.get(), "the focused window closes")

        // Profiler is open behind Console's tab spot: a click brings it to the front instead of closing it.
        val profiler = probes.getValue("window.profiler")
        assertTrue(profiler.isOpen.get())
        click("window.hierarchy") // focus elsewhere first
        click("window.profiler")
        assertTrue(profiler.isOpen.get() && profiler.visible, "brought to the front")
    }
}
