package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.editor.systems.PlayMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-45: the toolbar shows the same buttons in every mode; only their enabled state changes. */
class ViewportToolbarLayoutTest {
    private val gizmos = listOf(
        ToolbarButton.SELECT, ToolbarButton.TRANSLATE, ToolbarButton.ROTATE, ToolbarButton.SCALE,
        ToolbarButton.GIZMO_SPACE, ToolbarButton.SNAP, ToolbarButton.MEASURE,
    )

    @Test
    fun ToolbarLayout_EveryButtonAppearsOnce() {
        val all = ViewportToolbarLayout.GROUPS.flatten()
        assertEquals(ToolbarButton.entries.toSet(), all.toSet())
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun ToolbarLayout_GizmosAreGrayedOutNotHiddenInPlay() {
        assertTrue(ViewportToolbarLayout.GROUPS.first().containsAll(gizmos))
        gizmos.forEach {
            assertTrue(ViewportToolbarLayout.isEnabled(it, PlayMode.EDIT, canReset = false), "$it in Edit")
            assertFalse(ViewportToolbarLayout.isEnabled(it, PlayMode.PLAYING, canReset = true), "$it in Play")
            assertFalse(ViewportToolbarLayout.isEnabled(it, PlayMode.PAUSED, canReset = true), "$it in Pause")
        }
    }

    @Test
    fun ToolbarLayout_StopAndResetOnlyOutsideEdit() {
        assertFalse(ViewportToolbarLayout.isEnabled(ToolbarButton.STOP, PlayMode.EDIT, false))
        assertTrue(ViewportToolbarLayout.isEnabled(ToolbarButton.STOP, PlayMode.PLAYING, true))
        assertTrue(ViewportToolbarLayout.isEnabled(ToolbarButton.STOP, PlayMode.PAUSED, true))
        assertFalse(ViewportToolbarLayout.isEnabled(ToolbarButton.RESET, PlayMode.EDIT, false))
        assertTrue(ViewportToolbarLayout.isEnabled(ToolbarButton.RESET, PlayMode.PAUSED, true))
    }

    @Test
    fun ToolbarLayout_PlayPauseAndUtilitiesAlwaysEnabled() {
        PlayMode.entries.forEach { mode ->
            listOf(ToolbarButton.PLAY_PAUSE, ToolbarButton.PHYSICS_DEBUG, ToolbarButton.SCREENSHOT, ToolbarButton.HELP).forEach {
                assertTrue(ViewportToolbarLayout.isEnabled(it, mode, canReset = false), "$it in $mode")
            }
        }
    }

    @Test
    fun ToolbarLayout_PlayPauseShowsPauseOnlyWhilePlaying() {
        assertFalse(ViewportToolbarLayout.showsPause(PlayMode.EDIT))
        assertTrue(ViewportToolbarLayout.showsPause(PlayMode.PLAYING))
        assertFalse(ViewportToolbarLayout.showsPause(PlayMode.PAUSED))
    }

    @Test
    fun ToolbarLayout_HelpIsTheLastButton() {
        assertEquals(ToolbarButton.HELP, ViewportToolbarLayout.GROUPS.flatten().last())
    }
}
