package com.pafoid.skate.editor.imgui

import imgui.flag.ImGuiDockNodeFlags as PublicFlags
import imgui.internal.flag.ImGuiDockNodeFlags
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-88: requests from the headers, which windows draw while one fills the editor, and the viewport node's flags. */
class MaximizeSessionTest {

    private class W(name: String, private val dialog: Boolean = false) : EditorWindow(name) {
        override val isDialog: Boolean get() = dialog
        override fun imgui() {}
    }

    private val model = WindowMaximize(object : MaximizeLayout {
        override fun snapshot() = "ini"
        override fun reload(snapshot: String) {}
        override fun dockOverViewport(window: String) {}
        override fun focus(window: String) {}
    })

    @AfterEach
    fun teardown() = MaximizeSession.reset()

    @Test
    fun EveryDockedStandardWindow_CanMaximize_ButNotTheViewportOrDialogs() {
        assertTrue(W("window.script_editor").canMaximize)
        assertTrue(W("window.animation_editor").canMaximize)
        assertTrue(W("window.video_to_animation").canMaximize)
        assertTrue(W("window.console").canMaximize)
        assertFalse(W(MaximizeSession.VIEWPORT_WINDOW).canMaximize)
        assertFalse(W("window.editor_settings", dialog = true).canMaximize)
        assertFalse(W("window.search").canMaximize, "floating helper, no dock zone")
    }

    @Test
    fun Requests_UseTheDefaultMode_AndApplyInOrder() {
        MaximizeSession.defaultMode = { MaximizeMode.OVER_VIEWPORT }
        MaximizeSession.request("window.script_editor")
        MaximizeSession.request("window.console", MaximizeMode.FULL_EDITOR)
        val requests = MaximizeSession.drain()
        assertEquals(
            listOf(
                MaximizeSession.Request("window.script_editor", MaximizeMode.OVER_VIEWPORT),
                MaximizeSession.Request("window.console", MaximizeMode.FULL_EDITOR),
            ),
            requests,
        )
        assertTrue(MaximizeSession.drain().isEmpty(), "drained")

        MaximizeSession.apply(model, requests)
        assertEquals(WindowMaximize.State.Full("window.console"), model.state)
        MaximizeSession.apply(model, listOf(MaximizeSession.Request(null, null)))
        assertNull(model.window)
    }

    @Test
    fun WhileOneFillsTheEditor_OnlyItAndDialogsDraw() {
        val full = "window.script_editor"
        assertTrue(MaximizeSession.drawsWhileMaximized(W(full), full))
        assertTrue(MaximizeSession.drawsWhileMaximized(W("window.editor_settings", dialog = true), full))
        assertFalse(MaximizeSession.drawsWhileMaximized(W("window.console"), full))
        assertFalse(MaximizeSession.drawsWhileMaximized(W(MaximizeSession.VIEWPORT_WINDOW), full))
        assertTrue(MaximizeSession.drawsWhileMaximized(W("window.console"), null))
    }

    @Test
    fun TheHostArea_IsOnlyForTheFullEditorWindow() {
        val area = MaximizeSession.Area(0f, 20f, 1600f, 950f)
        MaximizeSession.hostArea = area
        assertNull(MaximizeSession.fullEditorArea("window.script_editor"), "no model")
        MaximizeSession.model = model
        model.toggle("window.script_editor", MaximizeMode.FULL_EDITOR)
        assertEquals(area, MaximizeSession.fullEditorArea("window.script_editor"))
        assertNull(MaximizeSession.fullEditorArea("window.console"))
        assertTrue(MaximizeSession.isMaximized("window.script_editor"))
    }

    @Test
    fun ViewportNode_AutoHidesItsTabBar_AndDropsTheOldNoTabBar() {
        // A layout saved before EDT-88: NoTabBar and HiddenTabBar (the misnamed "no close" bit).
        val old = ImGuiDockNodeFlags.CentralNode or ImGuiDockNodeFlags.NoTabBar or ImGuiDockNodeFlags.HiddenTabBar
        val fixed = DockLayoutFlags.centralFlags(old)
        assertEquals(0, fixed and ImGuiDockNodeFlags.NoTabBar)
        assertTrue(fixed and PublicFlags.AutoHideTabBar != 0)
        assertTrue(fixed and ImGuiDockNodeFlags.NoWindowMenuButton != 0)
        assertTrue(fixed and ImGuiDockNodeFlags.CentralNode != 0, "other flags kept")
        assertEquals(fixed, DockLayoutFlags.centralFlags(fixed), "idempotent")
    }
}
