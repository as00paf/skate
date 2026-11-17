package com.pafoid.skate.editor.imgui

/** Where a maximized tool window goes (EDT-88). */
@kotlinx.serialization.Serializable
enum class MaximizeMode(val labelKey: String) {
    /** Fills the editor between the menu bar and the status bar; the other panels are hidden until restored. */
    FULL_EDITOR("settings.editor.interface.maximize_mode.full_editor"),

    /** Docked as a tab in front of the Game Viewport; the side and bottom panels stay. */
    OVER_VIEWPORT("settings.editor.interface.maximize_mode.over_viewport"),
}

/**
 * What [WindowMaximize] asks of the dock layout. `ImGuiLayer` does it with ImGui calls; each call is queued and runs
 * at a safe point of the frame (a reload before `newFrame`, a dock before the windows draw, a focus after them).
 */
interface MaximizeLayout {
    /** Writes the layout to disk and returns it as ini text. */
    fun snapshot(): String

    /** Loads [snapshot] before the next frame. */
    fun reload(snapshot: String)

    /** Docks [window] into the Game Viewport's node. */
    fun dockOverViewport(window: String)

    /** Brings [window] to the front (of its tab, when docked). */
    fun focus(window: String)
}

/**
 * The maximize state (EDT-88, docs/design/EDT-88.md §2): at most one window is maximized, either over the editor
 * ([State.Full], the dock layout is not touched) or over the Game Viewport ([State.OverViewport], restored from the
 * layout [snapshot][MaximizeLayout.snapshot] taken before docking). Free of ImGui calls.
 */
class WindowMaximize(private val layout: MaximizeLayout) {

    sealed interface State {
        data object None : State
        data class Full(val window: String) : State
        data class OverViewport(val window: String, val snapshot: String) : State
    }

    var state: State = State.None
        private set

    /** The maximized window's name, or null. */
    val window: String?
        get() = when (val s = state) {
            State.None -> null
            is State.Full -> s.window
            is State.OverViewport -> s.window
        }

    val mode: MaximizeMode?
        get() = when (state) {
            State.None -> null
            is State.Full -> MaximizeMode.FULL_EDITOR
            is State.OverViewport -> MaximizeMode.OVER_VIEWPORT
        }

    /** The window drawn over the whole editor, or null. */
    val fullEditorWindow: String? get() = (state as? State.Full)?.window

    /** True while the layout on screen is a temporary one that must not be saved (ImGui's autosave is off). */
    val holdsSnapshot: Boolean get() = state is State.OverViewport

    fun isMaximized(window: String): Boolean = this.window == window

    /**
     * Maximizes [window] in [mode], or restores it when it is the maximized one. Maximizing another window restores
     * the current one first.
     */
    fun toggle(window: String, mode: MaximizeMode) {
        if (this.window == window) {
            restore()
            return
        }
        // Switching from one window over the viewport to another: the layout to come back to is still the first
        // snapshot; reload it, then dock the new window (both queued, in that order).
        val previous = (state as? State.OverViewport)?.snapshot
        restore(focusWindow = false)
        state = when (mode) {
            MaximizeMode.FULL_EDITOR -> State.Full(window)
            MaximizeMode.OVER_VIEWPORT -> {
                val snapshot = previous ?: layout.snapshot()
                layout.dockOverViewport(window)
                State.OverViewport(window, snapshot)
            }
        }
        layout.focus(window)
    }

    /**
     * Puts the layout back as it was before maximizing. From the full editor the window keeps the focus, back in its
     * tab (the dock layout never changed). Over the viewport the snapshot alone decides which tabs are in front, so
     * the layout comes back exactly.
     */
    fun restore() = restore(focusWindow = true)

    private fun restore(focusWindow: Boolean) {
        val s = state
        state = State.None
        when (s) {
            State.None -> {}
            is State.Full -> if (focusWindow) layout.focus(s.window)
            is State.OverViewport -> layout.reload(s.snapshot)
        }
    }

    /**
     * Play started (§6): a full-editor window is restored so the game is visible; over the viewport, the Game
     * Viewport's tab comes to the front. Stop does not re-maximize.
     */
    fun onPlay(viewportWindow: String) {
        when (state) {
            State.None -> {}
            is State.Full -> restore()
            is State.OverViewport -> layout.focus(viewportWindow)
        }
    }

    /** [window] was closed: restore the layout if it was the maximized one. */
    fun onWindowClosed(window: String) {
        if (this.window == window) restore(focusWindow = false)
    }

    /** Another docked window was asked for (View menu, Help, …) while one fills the editor: show the layout again. */
    fun onWindowShown(window: String) {
        if (fullEditorWindow != null && fullEditorWindow != window) restore(focusWindow = false)
    }

    /** Reset Layout replaces the layout anyway: drop the state without reloading the snapshot. */
    fun onResetLayout() {
        state = State.None
    }

    /**
     * The editor is closing: drops the state and returns the layout from before maximizing over the viewport, which
     * is the one to save, or null when the layout on screen is the real one.
     */
    fun onExit(): String? = (state as? State.OverViewport)?.snapshot.also { state = State.None }
}
