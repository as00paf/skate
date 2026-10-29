package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorDialogTest {
    private class FakeDialog : EditorDialog("window.fake_dialog", mockk<StringManager>(relaxed = true)) {
        override fun content() {}
    }

    private class FakeWindow(name: String) : EditorWindow(name) {
        override fun imgui() {}
    }

    @Test
    fun EditorDialog_OpenCloseToggle_UpdateState() {
        val d = FakeDialog()
        assertFalse(d.isOpen.get())
        d.open()
        assertTrue(d.isOpen.get())
        d.toggle()
        assertFalse(d.isOpen.get())
        d.toggle(); d.close()
        assertFalse(d.isOpen.get())
    }

    @Test
    fun WindowRegistry_Dockable_ExcludesDialogs() {
        val windows = listOf(FakeWindow("window.a"), FakeDialog(), FakeWindow("window.b"))
        assertEquals(listOf("window.a", "window.b"), WindowRegistry.dockable(windows).map { it.name })
    }

    @Test
    fun EditorLayoutStore_StripDockIds_RemovesOnlyDialogDockIdKeepingGeometry() {
        val ini = "[Window][Input Testing]\nPos=10,20\nSize=500,600\nCollapsed=0\nDockId=0x00000005,1\n\n" +
            "[Window][Console]\nPos=0,0\nDockId=0x00000006,0\n"
        val out = EditorLayoutStore.stripDockIds(ini, listOf("Input Testing"))
        assertFalse(out.contains("DockId=0x00000005"))
        assertTrue(out.contains("Pos=10,20"))
        assertTrue(out.contains("Size=500,600"))
        assertTrue(out.contains("DockId=0x00000006,0"))
    }
}
