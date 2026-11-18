package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.help.HelpRouter
import com.pafoid.skate.editor.imgui.DefaultDockLayout.Zone
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * EDT-101 (option B) with real docked windows: a docked window without tools gets Maximize and `?` over the top-right
 * of its content (no row of their own: its first line starts on the buttons' row), the buttons keep their clicks even
 * over a full-width item, and a docked window with tools keeps them on the tools row.
 */
class HeaderPlacementTest {

    private val strings: StringManager = mockk(relaxed = true)

    private class Probe(name: String, private val strings: StringManager, private val tools: (() -> Unit)?) : EditorWindow(name) {
        override val icon: String = Icons.LIST
        var headerTop = 0f
        var firstLineTop = 0f
        var sectionOpen = false

        override fun imgui() {
            if (beginWindow(strings)) {
                headerTop = ImGui.getCursorScreenPosY()
                header(strings, tools)
                firstLineTop = ImGui.getCursorScreenPosY()
                // A full-width item under the corner: it must not take the buttons' clicks.
                sectionOpen = ImGui.collapsingHeader("Section##probe", ImGuiTreeNodeFlags.None)
            }
            ImGui.end()
            WindowHeader.drawCornerOverlay(this)
        }
    }

    private val bare = Probe("window.properties", strings, null)
    private val withTools = Probe("window.console", strings) { ImGui.text("tools") }
    private val dock = HeadlessDock(
        linkedMapOf(
            bare.title(strings) to Zone.RIGHT,
            "Viewport###window.game_viewport" to Zone.CENTER,
            withTools.title(strings) to Zone.BOTTOM,
        ),
    )
    private var helpOpened = 0
    private val helpBefore = HelpRouter.open

    @BeforeEach
    fun setup() {
        dock.create()
        listOf(bare, withTools).forEach { it.isOpen.set(true) }
        HelpRouter.open = { helpOpened++ }
    }

    @AfterEach
    fun teardown() {
        HelpRouter.open = helpBefore
        dock.destroy()
    }

    private fun frame() = dock.frame(windows = {
        ImGui.begin("Viewport###window.game_viewport"); ImGui.end()
        bare.imgui()
        withTools.imgui()
    })

    @Test
    fun DockedWithoutTools_ButtonsShareTheFirstLine() {
        repeat(3) { frame() }
        assertTrue(ImGui.getFrameCount() > 0)
        assertEquals(bare.headerTop, bare.firstLineTop, "no row of their own: the content starts on the buttons' row")
        val help = WindowHeader.lastDrawn
        assertNotNull(help)
        assertNull(bare.cornerOverlay, "the overlay was painted and cleared")
    }

    @Test
    fun DockedWithoutTools_TheCornerButtonsKeepTheirClicks() {
        repeat(3) { frame() }
        // The `?` of the bare window: drawn first, so the collapsing header under it must not react.
        withTools.isOpen.set(false)
        repeat(2) { frame() }
        val (help, _) = WindowHeader.lastDrawn!!
        val io = ImGui.getIO()
        io.addMousePosEvent((help.minX + help.maxX) / 2f, (help.minY + help.maxY) / 2f)
        frame()
        io.addMouseButtonEvent(0, true)
        frame()
        io.addMouseButtonEvent(0, false)
        frame()
        frame()
        assertEquals(1, helpOpened, "the ? opened the help")
        assertFalse(bare.sectionOpen, "the item under the ? did not get the click")
    }

    @Test
    fun DockedWithTools_ButtonsStayOnTheToolsRow() {
        repeat(3) { frame() }
        assertTrue(withTools.firstLineTop > withTools.headerTop, "the tools row is a row")
        // The tools window drew last: its `?` sits on the tools row (the row's top), never alone below it.
        val (help, _) = WindowHeader.lastDrawn!!
        assertTrue(help.minY < withTools.firstLineTop, "the ? is on the tools row")
        assertTrue(help.minY >= withTools.headerTop - 0.5f)
    }
}
