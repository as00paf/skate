package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.DefaultDockLayout.Zone
import imgui.ImGui
import imgui.internal.ImGui as Internal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * EDT-88 step 1: ImGui documents ini loading as a start-up call. This proves that a docking layout saved to memory can
 * be reloaded between frames, after a window was docked elsewhere, and comes back byte for byte.
 */
class LayoutSnapshotReloadSpikeTest {

    private val zones = linkedMapOf(
        "Hierarchy###window.hierarchy" to Zone.LEFT,
        "Viewport###window.game_viewport" to Zone.CENTER,
        "Properties###window.properties" to Zone.RIGHT,
        "Console###window.console" to Zone.BOTTOM,
        "Script Editor###window.script_editor" to Zone.BOTTOM,
    )
    private val dock = HeadlessDock(zones)

    @BeforeEach
    fun setup() = dock.create()

    @AfterEach
    fun teardown() = dock.destroy()

    @Test
    fun SnapshotReload_BetweenFrames_RestoresTheLayoutExactly() {
        dock.frames(4)
        val before = ImGui.saveIniSettingsToMemory()
        val dockIdsBefore = HashMap(dock.dockIds)
        assertTrue("[Docking][Data]" in before && "DockSpace" in before, before)
        assertEquals(4, dockIdsBefore.values.toSet().size, "four zones: $dockIdsBefore")

        val script = "Script Editor###window.script_editor"
        Internal.dockBuilderDockWindow(script, dock.centralId)
        dock.frames(3)
        assertEquals(dock.centralId, dock.dockIds[script], "docked over the viewport")
        assertNotEquals(before, ImGui.saveIniSettingsToMemory(), "the layout changed while docked")

        ImGui.loadIniSettingsFromMemory(before)
        dock.frames(3)

        assertEquals(before, ImGui.saveIniSettingsToMemory())
        assertEquals(dockIdsBefore, dock.dockIds)
    }

    @Test
    fun SnapshotReload_RebuildsASplitThatDockingRemoved() {
        // A window alone in its node: docking it elsewhere deletes the node and merges the split.
        val alone = "Properties###window.properties"
        dock.frames(4)
        val before = ImGui.saveIniSettingsToMemory()
        val dockIdsBefore = HashMap(dock.dockIds)

        Internal.dockBuilderDockWindow(alone, dock.centralId)
        dock.frames(3)
        assertEquals(dock.centralId, dock.dockIds[alone])

        ImGui.loadIniSettingsFromMemory(before)
        dock.frames(3)

        assertEquals(before, ImGui.saveIniSettingsToMemory())
        assertEquals(dockIdsBefore, dock.dockIds)
    }
}
