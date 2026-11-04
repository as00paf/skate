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
        assertEquals(listOf(WindowCategory.SCENE, WindowCategory.DEBUG, WindowCategory.INPUT, WindowCategory.SCRIPTING), grouped.keys.toList())
        assertEquals("window.keybindings", grouped.getValue(WindowCategory.INPUT).single().name)
        assertFalse(grouped.values.flatten().any { it.name == "window.search" })
    }
}
