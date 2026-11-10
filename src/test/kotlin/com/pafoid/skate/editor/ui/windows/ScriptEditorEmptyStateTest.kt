package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.ui.windows.ScriptEditorWindow.EmptyKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** EDT-52H: what the Script Editor shows when no script tab is open. */
class ScriptEditorEmptyStateTest {

    @Test
    fun EmptyState_WithoutProject_AsksForAProject() {
        assertEquals(EmptyKind.NO_PROJECT, ScriptEditorWindow.emptyState(hasProject = false))
    }

    @Test
    fun EmptyState_WithProject_SaysNoScriptIsOpen() {
        assertEquals(EmptyKind.NO_SCRIPT, ScriptEditorWindow.emptyState(hasProject = true))
    }
}
