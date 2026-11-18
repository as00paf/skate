package com.pafoid.skate.editor.data

import com.pafoid.skate.editor.imgui.EditorLayoutStore
import com.pafoid.skate.engine.input.InputBinding
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lwjgl.glfw.GLFW
import java.io.File

/** EDT-84 Q6: one rebindable Alt shortcut per tool window. */
class ToolWindowShortcutsTest {
    @TempDir
    lateinit var dir: File

    private val m = EditorInputMappings()

    @Test
    fun EveryToolWindow_HasAShortcut_InTheToolWindowsTab() {
        ToolWindowShortcuts.WINDOWS.forEach { window ->
            val shortcut = EditorShortcuts.toolWindow(window)
            assertTrue(shortcut != null && shortcut.rebindable && shortcut.alt, window)
            assertEquals(ShortcutGroup.TOOL_WINDOWS, shortcut!!.group)
            assertEquals(window, shortcut.labelKey, "labelled with the window's title")
        }
        assertEquals(ToolWindowShortcuts.WINDOWS.size, EditorShortcuts.TOOL_WINDOWS.size)
    }

    @Test
    fun Defaults_AreAltDigits_AndTheRestUnbound() {
        assertEquals("Alt+1", EditorShortcuts.toolWindow("window.project")!!.display(m))
        assertEquals("Alt+4", EditorShortcuts.toolWindow("window.console")!!.display(m))
        assertEquals("Alt+F12", EditorShortcuts.toolWindow("window.shell")!!.display(m))
        val help = EditorShortcuts.toolWindow("window.help")!!
        assertEquals(KeyCombo.UNBOUND, help.display(m))
        assertTrue(!help.combo(m).isBound)
    }

    @Test
    fun NoDefaultConflicts_AndUnboundShortcutsNeverConflict() {
        assertEquals(emptyList<Pair<EditorShortcut, EditorShortcut>>(), EditorShortcuts.conflicts(m))
        val console = EditorShortcuts.toolWindow("window.console")!!
        val help = EditorShortcuts.toolWindow("window.help")!!
        help.binding!!.set(m, InputBinding(keyboardKey = GLFW.GLFW_KEY_4))
        assertEquals(listOf(help), EditorShortcuts.conflictsOf(console, m))
    }

    @Test
    fun Rebinding_IsSaved_AndResetRestoresTheDefault() {
        val console = EditorShortcuts.toolWindow("window.console")!!
        console.binding!!.set(m, InputBinding(keyboardKey = GLFW.GLFW_KEY_C))
        assertEquals("Alt+C", console.display(m))
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val back = json.decodeFromString(EditorInputMappings.serializer(), json.encodeToString(EditorInputMappings.serializer(), m))
        assertEquals("Alt+C", console.display(back))
        m.resetToDefault()
        assertEquals("Alt+4", console.display(m))
        assertEquals(EditorInputMappings(), m)
    }

    @Test
    fun SettingsSavedBefore_UseTheDefaults() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(EditorInputMappings.serializer(), "{}")
        assertEquals("Alt+1", EditorShortcuts.toolWindow("window.project")!!.display(old))
        old.toolWindows = mapOf("window.console" to InputBinding(keyboardKey = GLFW.GLFW_KEY_C))
        assertEquals("Alt+2", EditorShortcuts.toolWindow("window.hierarchy")!!.display(old), "a window missing from the map")
    }

    @Test
    fun TheBarsPlacement_IsSavedWithTheLayout_AndResetDeletesIt() {
        val store = EditorLayoutStore(File(dir, "imgui.ini"), File(dir, "imgui.windows"), File(dir, "imgui.toolbars"))
        assertNull(store.loadToolBars())
        store.saveToolBars("LEFT_TOP=window.console")
        assertEquals("LEFT_TOP=window.console", store.loadToolBars())
        store.clear()
        assertNull(store.loadToolBars())
    }
}
