package com.pafoid.skate.editor

import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.WindowCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class WindowCategoryTest {
    private class W(name: String) : EditorWindow(name) { override fun imgui() {} }

    @Test
    fun WindowCategory_GroupsWindowsAndSkipsUncategorised() {
        val grouped = WindowCategory.grouped(
            listOf(W("window.console"), W("window.hierarchy"), W("window.script_editor"), W("window.search"), W("window.keybindings")),
        )
        assertEquals(listOf(WindowCategory.SCENE, WindowCategory.DEBUG, WindowCategory.SCRIPTING), grouped.keys.toList())
        assertFalse(grouped.values.flatten().any { it.name == "window.search" })
        // Dialogs open from the gear and Settings menus, not View > Windows (EDT-64).
        assertFalse(grouped.values.flatten().any { it.name == "window.keybindings" })
    }

    @Test
    fun WindowCategory_SettingsDialogsHaveNoCategory() {
        listOf("window.keybindings", "window.editor_settings", "window.project_settings").forEach {
            assertEquals(null, WindowCategory.of(W(it)), it)
        }
    }
}
