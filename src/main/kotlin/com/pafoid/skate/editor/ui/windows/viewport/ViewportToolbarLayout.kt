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
        listOf(ToolbarButton.SELECT, ToolbarButton.TRANSLATE, ToolbarButton.ROTATE, ToolbarButton.SCALE, ToolbarButton.MEASURE),
        listOf(ToolbarButton.PLAY_PAUSE, ToolbarButton.STOP),
        listOf(ToolbarButton.RESET, ToolbarButton.PHYSICS_DEBUG, ToolbarButton.SCREENSHOT),
        // EDT-78: World/Local and Snap sit at the end, just before the `?`.
        listOf(ToolbarButton.GIZMO_SPACE, ToolbarButton.SNAP),
        listOf(ToolbarButton.HELP),
    )

    /** Edit-only buttons: the gizmo tools and their World/Local and Snap options. */
    val GIZMOS: Set<ToolbarButton> = setOf(
        ToolbarButton.SELECT, ToolbarButton.TRANSLATE, ToolbarButton.ROTATE, ToolbarButton.SCALE, ToolbarButton.MEASURE,
        ToolbarButton.GIZMO_SPACE, ToolbarButton.SNAP,
    )

    fun isEnabled(button: ToolbarButton, mode: PlayMode, canReset: Boolean): Boolean = when (button) {
        in GIZMOS -> mode == PlayMode.EDIT
        ToolbarButton.STOP -> mode != PlayMode.EDIT
        ToolbarButton.RESET -> canReset
        else -> true
    }

    /**
     * The toolbar's total width: every slot's [slotWidth], [buttonSpacing] between buttons of a group and
     * [separatorWidth] between groups. Text buttons (World/Local, Snap) are wider than the square ones, so the width
     * comes from each slot rather than a fixed button size; otherwise the end of the toolbar, the `?`, gets clipped.
     */
    fun totalWidth(
        groups: List<List<ToolbarButton>>,
        buttonSpacing: Float,
        separatorWidth: Float,
        slotWidth: (ToolbarButton) -> Float,
    ): Float {
        val buttons = groups.sumOf { group -> group.sumOf { slotWidth(it).toDouble() } }.toFloat()
        val spacings = groups.sumOf { (it.size - 1).coerceAtLeast(0) }
        val separators = (groups.size - 1).coerceAtLeast(0)
        return buttons + buttonSpacing * spacings + separatorWidth * separators
    }

    /** The Play/Pause button shows Pause while playing, Play otherwise (start in Edit, resume in Paused). */
    fun showsPause(mode: PlayMode): Boolean = mode == PlayMode.PLAYING
}
