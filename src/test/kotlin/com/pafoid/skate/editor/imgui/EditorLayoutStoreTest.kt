package com.pafoid.skate.editor.imgui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EditorLayoutStoreTest {
    @TempDir
    lateinit var dir: File

    private fun store() = EditorLayoutStore(File(dir, "imgui.ini"), File(dir, "imgui.windows"))

    @Test
    fun LayoutStore_NoFiles_HasNoSavedState() {
        val store = store()
        assertFalse(store.hasSavedDockLayout())
        assertNull(store.loadOpenWindows())
    }

    @Test
    fun LayoutStore_EmptyIni_IsNotSavedLayout() {
        File(dir, "imgui.ini").writeText("")
        assertFalse(store().hasSavedDockLayout())
    }

    @Test
    fun LayoutStore_SaveThenLoad_RoundTripsOpenWindows() {
        val store = store()
        store.saveOpenWindows(listOf("window.hierarchy", "window.console"))
        assertEquals(setOf("window.hierarchy", "window.console"), store().loadOpenWindows())
    }

    @Test
    fun LayoutStore_SaveNoWindows_LoadsEmptySet() {
        val store = store()
        store.saveOpenWindows(emptyList())
        assertEquals(emptySet<String>(), store.loadOpenWindows())
    }

    @Test
    fun LayoutStore_Clear_RemovesIniAndWindows() {
        val store = store()
        File(dir, "imgui.ini").writeText("[Window][x]")
        store.saveOpenWindows(listOf("a"))
        assertTrue(store.hasSavedDockLayout())
        store.clear()
        assertFalse(store.hasSavedDockLayout())
        assertNull(store.loadOpenWindows())
    }
}
