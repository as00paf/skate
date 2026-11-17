package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.DefaultDockLayout.Zone
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

/**
 * EDT-88: real ImGui frames (headless, docking on) with the editor's default zones, the real [EditorWindow] header
 * and [MaximizeDriver]. Each of the three tool windows is maximized and restored in both modes, and the layout
 * (`saveIniSettingsToMemory()` and every window's dock node) must come back exactly.
 */
class MaximizeLayoutRestoreTest {

    private val strings: StringManager = mockk(relaxed = true)

    private class Probe(name: String, private val strings: StringManager, private val viewport: Boolean = false) : EditorWindow(name) {
        var drawn = false
        var visible = false
        var dockId = 0
        var rect = MaximizeSession.Area(0f, 0f, 0f, 0f)

        override fun imgui() {
            drawn = true
            visible = if (viewport) {
                beginWindow(strings, ImGuiWindowFlags.NoTitleBar, closable = false)
            } else {
                beginWindow(strings)
            }
            dockId = ImGui.getWindowDockID()
            rect = MaximizeSession.Area(ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight())
            if (visible && !viewport) header(strings)
            ImGui.text(name)
            ImGui.end()
        }
    }

    private val zones = linkedMapOf(
        "window.hierarchy" to Zone.LEFT,
        "window.game_viewport" to Zone.CENTER,
        "window.properties" to Zone.RIGHT,
        "window.console" to Zone.BOTTOM,
        "window.script_editor" to Zone.BOTTOM,
        "window.animation_editor" to Zone.BOTTOM,
        "window.video_to_animation" to Zone.BOTTOM,
    )
    private val probes = zones.keys.associateWith { Probe(it, strings, viewport = it == MaximizeSession.VIEWPORT_WINDOW) }
    private val autosave = mutableListOf<Boolean>()
    private var saves = 0
    private val driver = MaximizeDriver(
        windowOf = { probes[it] },
        titleOf = { window, id -> window.title(strings, id = id) },
        saveLayout = { saves++ },
        setAutosave = { autosave += it },
    )
    private val dock = HeadlessDock(
        zones.mapKeys { (name, _) -> probes.getValue(name).title(strings) },
        centralFlags = { DockLayoutFlags.applyCentral(it) },
    )
    private var playing = false

    @BeforeEach
    fun setup() {
        dock.create()
        MaximizeSession.reset()
        MaximizeSession.model = driver.model
        probes.values.forEach { it.isOpen.set(true) }
    }

    @AfterEach
    fun teardown() {
        MaximizeSession.reset()
        dock.destroy()
    }

    private fun frame() {
        driver.beforeFrame()
        dock.frame(
            dockSpaceFlags = driver.dockSpaceFlags(),
            beforeDockSpace = {
                MaximizeSession.hostArea = MaximizeSession.Area(
                    ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY(), ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(),
                )
            },
            afterDockSpace = { driver.afterDockSpace(it) },
            windows = {
                val full = driver.model.fullEditorWindow
                probes.values.forEach { p ->
                    p.drawn = false
                    if (p.isOpen.get() && MaximizeSession.drawsWhileMaximized(p, full)) p.imgui()
                }
            },
            afterWindows = { driver.afterWindows(playing) },
        )
    }

    private fun frames(n: Int) = repeat(n) { frame() }

    private fun dockIds() = probes.mapValues { it.value.dockId }

    /** The default layout with [window] as the front tab of its zone, as when its own button or tab is used. */
    private fun settle(window: String): Pair<String, Map<String, Int>> {
        frames(3)
        driver.focus(window)
        frames(3)
        assertTrue(probes.getValue(window).visible, "$window is the front tab")
        return ImGui.saveIniSettingsToMemory() to dockIds()
    }

    @ParameterizedTest
    @ValueSource(strings = ["window.script_editor", "window.animation_editor", "window.video_to_animation"])
    fun FullEditor_FillsTheDockSpace_ThenRestoresTheLayoutExactly(window: String) {
        val (ini, ids) = settle(window)

        MaximizeSession.request(window, MaximizeMode.FULL_EDITOR)
        frames(3)

        assertEquals(window, driver.model.fullEditorWindow)
        val probe = probes.getValue(window)
        assertTrue(probe.drawn && probe.visible)
        assertEquals(MaximizeSession.hostArea, probe.rect, "the host covers the dock space")
        assertEquals(0, probe.dockId, "the host is not docked")
        probes.filterKeys { it != window }.values.forEach { assertFalse(it.drawn, "${it.name} is hidden") }
        assertEquals(ini, ImGui.saveIniSettingsToMemory(), "the dock layout is untouched while maximized")
        assertTrue(autosave.isEmpty(), "autosave stays on: nothing temporary to protect")

        MaximizeSession.request(window)
        frames(3)

        assertNull(driver.model.window)
        probes.values.forEach { assertTrue(it.drawn, "${it.name} draws again") }
        assertEquals(ini, ImGui.saveIniSettingsToMemory())
        assertEquals(ids, dockIds())
        assertTrue(probe.visible)
    }

    @ParameterizedTest
    @ValueSource(strings = ["window.script_editor", "window.animation_editor", "window.video_to_animation"])
    fun OverViewport_DocksInFrontOfTheViewport_ThenRestoresTheLayoutExactly(window: String) {
        val (ini, ids) = settle(window)
        val viewport = probes.getValue(MaximizeSession.VIEWPORT_WINDOW)
        assertTrue(dock.central().isHiddenTabBar, "the viewport alone shows no tab bar")

        MaximizeSession.request(window, MaximizeMode.OVER_VIEWPORT)
        frames(3)

        val probe = probes.getValue(window)
        assertEquals(dock.centralId, probe.dockId, "docked in the viewport's node")
        assertTrue(probe.visible, "in front")
        assertFalse(viewport.visible, "the viewport is the hidden tab")
        assertFalse(dock.central().isHiddenTabBar, "two windows: the tab bar shows")
        assertEquals(listOf(false), autosave, "autosave is off while the temporary layout shows")
        assertEquals(1, saves, "the layout was written to disk before docking")
        assertNotEquals(ini, ImGui.saveIniSettingsToMemory())

        MaximizeSession.requestRestore()
        frames(3)

        assertNull(driver.model.window)
        assertEquals(ini, ImGui.saveIniSettingsToMemory())
        assertEquals(ids, dockIds())
        assertTrue(viewport.visible && probe.visible)
        assertTrue(dock.central().isHiddenTabBar, "alone again: no tab bar")
        assertEquals(listOf(false, true), autosave, "autosave is back on")
    }

    @Test
    fun OverViewport_SwitchingWindows_StillRestoresTheFirstLayout() {
        val (ini, ids) = settle("window.script_editor")
        MaximizeSession.request("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        frames(3)
        MaximizeSession.request("window.animation_editor", MaximizeMode.OVER_VIEWPORT)
        frames(3)
        assertEquals(dock.centralId, probes.getValue("window.animation_editor").dockId)
        assertEquals(ids.getValue("window.script_editor"), probes.getValue("window.script_editor").dockId, "the first one went back")

        MaximizeSession.requestRestore()
        frames(3)
        assertEquals(ini, ImGui.saveIniSettingsToMemory())
        assertEquals(ids, dockIds())
    }

    @Test
    fun Play_OverViewport_BringsTheViewportTabToTheFront() {
        settle("window.script_editor")
        MaximizeSession.request("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        frames(3)
        playing = true
        frames(3)
        assertTrue(probes.getValue(MaximizeSession.VIEWPORT_WINDOW).visible)
        assertFalse(probes.getValue("window.script_editor").visible)
        assertEquals("window.script_editor", driver.model.window, "still maximized: its tab stays beside the viewport")
    }

    @Test
    fun Exit_WhenNotMaximizedOverViewport_SavesTheLayoutOnScreen() {
        settle("window.script_editor")
        MaximizeSession.request("window.script_editor", MaximizeMode.FULL_EDITOR)
        frames(2)
        assertNull(driver.onExit())
        assertNull(driver.model.window)
    }

    @Test
    fun Play_FullEditor_Restores() {
        val (ini, _) = settle("window.script_editor")
        MaximizeSession.request("window.script_editor", MaximizeMode.FULL_EDITOR)
        frames(2)
        playing = true
        frames(3)
        assertNull(driver.model.window)
        assertTrue(probes.getValue(MaximizeSession.VIEWPORT_WINDOW).drawn)
        assertEquals(ini, ImGui.saveIniSettingsToMemory())
    }

    @Test
    fun ClosingTheMaximizedWindow_RestoresTheLayout() {
        val (_, ids) = settle("window.script_editor")
        MaximizeSession.request("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        frames(3)
        probes.getValue("window.script_editor").isOpen.set(false)
        frames(3)
        assertNull(driver.model.window)
        assertTrue(dock.central().isHiddenTabBar, "the viewport is alone again")
        probes.getValue("window.script_editor").isOpen.set(true)
        frames(3)
        // Back in its own node. Like any closed and reopened tab, ImGui puts it last in the tab order.
        assertEquals(ids, dockIds())
    }

    @Test
    fun Exit_WhileMaximizedOverViewport_LeavesTheOriginalLayoutToSave() {
        val (ini, _) = settle("window.script_editor")
        MaximizeSession.request("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        frames(3)
        assertEquals(ini, driver.onExit(), "the layout to save is the one from before maximizing")
        assertNull(driver.model.window)
        assertEquals(listOf(false), autosave, "autosave stays off, so ImGui's save at shutdown cannot overwrite it")
    }
}
