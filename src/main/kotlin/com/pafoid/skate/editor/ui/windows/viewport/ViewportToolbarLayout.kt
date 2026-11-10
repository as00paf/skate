package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.editor.systems.PlayMode

/** The viewport toolbar's buttons. */
enum class ToolbarButton {
    SELECT, TRANSLATE, ROTATE, SCALE, GIZMO_SPACE, SNAP, MEASURE,
    PLAY_PAUSE, STOP,
    RESET, PHYSICS_DEBUG, SCREENSHOT,
    /** The viewport's `?` (EDT-51 §2.2: the viewport has no title bar, so its help sits at the end of the toolbar). */
    HELP,
}

/**
 * Which toolbar buttons exist and which are usable (EDT-45). The same buttons are shown in every [PlayMode], so the
 * toolbar never shifts: buttons that make no sense in a mode are grayed out instead of hidden.
 */
object ViewportToolbarLayout {
    val GROUPS: List<List<ToolbarButton>> = listOf(
        listOf(
            ToolbarButton.SELECT, ToolbarButton.TRANSLATE, ToolbarButton.ROTATE, ToolbarButton.SCALE,
            ToolbarButton.GIZMO_SPACE, ToolbarButton.SNAP, ToolbarButton.MEASURE,
        ),
        listOf(ToolbarButton.PLAY_PAUSE, ToolbarButton.STOP),
        listOf(ToolbarButton.RESET, ToolbarButton.PHYSICS_DEBUG, ToolbarButton.SCREENSHOT),
        listOf(ToolbarButton.HELP),
    )

    private val GIZMOS = GROUPS.first().toSet()

    fun isEnabled(button: ToolbarButton, mode: PlayMode, canReset: Boolean): Boolean = when (button) {
        in GIZMOS -> mode == PlayMode.EDIT
        ToolbarButton.STOP -> mode != PlayMode.EDIT
        ToolbarButton.RESET -> canReset
        else -> true
    }

    /** The Play/Pause button shows Pause while playing, Play otherwise (start in Edit, resume in Paused). */
    fun showsPause(mode: PlayMode): Boolean = mode == PlayMode.PLAYING
}
