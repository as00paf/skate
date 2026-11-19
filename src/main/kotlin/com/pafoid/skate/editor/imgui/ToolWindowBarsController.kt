package com.pafoid.skate.editor.imgui

/**
 * What the tool window bars do (EDT-84 §4, §5, §6): toggles a window like IntelliJ (from a button or the window's
 * shortcut), moves buttons and re-docks their windows on the new side, and saves the placement on every change.
 * Showing, focusing and closing windows is view state, not an undoable Command.
 */
class ToolWindowBarsController(
    private val windowOf: (String) -> EditorWindow?,
    /** The placement saved by [save] last time, or null. */
    load: () -> String?,
    private val save: (String) -> Unit,
    /** Opens [window] (`WindowAction.Show`, which also restores a full-editor maximize, EDT-88). */
    private val show: (String) -> Unit,
    /** Brings [window] to the front and focuses it, after its next `begin`. */
    private val focus: (String) -> Unit,
    private val docks: DefaultDockLayout.DockMemory = DefaultDockLayout.docks,
) {
    var bars: ToolWindowBars = ToolWindowBars.decode(load())
        private set

    fun state(window: String): ToolWindowState =
        ToolWindowStates.state(window, windowOf(window)?.isOpen?.get() == true)

    /** A click on [window]'s button (with its [seen] state when pressed), or its shortcut. */
    fun toggle(window: String, seen: ToolWindowState = state(window)) {
        val w = windowOf(window) ?: return
        when (ToolWindowBars.click(seen)) {
            ToolWindowClick.OPEN -> {
                show(window)
                focus(window)
            }
            ToolWindowClick.FOCUS -> focus(window)
            ToolWindowClick.CLOSE -> w.isOpen.set(false)
        }
    }

    /**
     * Moves [window]'s button to [index] of [section] and saves. When it changes section its window is re-docked there
     * (design Q3): next to a window whose button is in that section, else with a window of the section's default zone.
     */
    fun move(window: String, section: BarSection, index: Int) {
        val changed = bars.move(window, section, index)
        if (changed) {
            val others = bars.windows(section) - window
            val target = docks.dockOfAny(others) ?: docks.dockOfAny(DefaultDockLayout.windowsIn(section.zone) - window)
            if (target != null) windowOf(window)?.dockRequest = target
        }
        save(bars.encode())
    }

    /** Reset Layout: every button back to its default section. */
    fun reset() {
        bars.reset()
    }

    /** The placement to save with the layout. */
    fun encode(): String = bars.encode()
}
