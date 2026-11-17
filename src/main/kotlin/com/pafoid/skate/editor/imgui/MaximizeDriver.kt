package com.pafoid.skate.editor.imgui

import imgui.ImGui
import imgui.flag.ImGuiDockNodeFlags
import imgui.internal.ImGuiDockNode
import imgui.internal.ImGuiWindow
import imgui.internal.ImGui as Internal

/**
 * Runs [WindowMaximize]'s layout changes at safe points of the frame (EDT-88, docs/design/EDT-88.md §3, §4, §8).
 * `ImGuiLayer` calls it in this order each frame:
 * 1. [beforeFrame], before `newFrame`: reloads a layout snapshot and switches ImGui's autosave;
 * 2. [dockSpaceFlags] for the dock space, then [afterDockSpace] with its central node: fixes the node's flags and
 *    docks a window over the viewport;
 * 3. the windows draw ([MaximizeSession.drawsWhileMaximized]);
 * 4. [afterWindows]: focus, this frame's requests, and the close / play rules.
 */
class MaximizeDriver(
    private val windowOf: (String) -> EditorWindow?,
    /** [EditorWindow.title] with the given ImGui id. */
    private val titleOf: (EditorWindow, String) -> String,
    /** Writes the layout and the open-window list to disk. */
    private val saveLayout: () -> Unit,
    /** Turns ImGui's ini autosave on or off. */
    private val setAutosave: (Boolean) -> Unit,
) : MaximizeLayout {
    val model = WindowMaximize(this)

    private var pendingReload: String? = null
    private val pendingDocks = ArrayList<String>()
    private val pendingFocus = ArrayList<String>()
    private var autosaveOn = true
    private var wasPlaying = false

    override fun snapshot(): String {
        saveLayout()
        return ImGui.saveIniSettingsToMemory()
    }

    override fun reload(snapshot: String) {
        pendingDocks.clear()
        pendingReload = snapshot
    }

    override fun dockOverViewport(window: String) {
        pendingDocks += window
    }

    override fun focus(window: String) {
        pendingFocus += window
    }

    /** Before `newFrame`: ImGui reloads a layout only between frames. Autosave is off while a snapshot is held. */
    fun beforeFrame() {
        pendingReload?.let {
            pendingReload = null
            ImGui.loadIniSettingsFromMemory(it)
            // A docked window that has the focus becomes its node's front tab; with no focus, the snapshot's front
            // tabs stay (the window that was maximized would otherwise come out in front of its old node).
            Internal.focusWindow(ImGuiWindow(0))
        }
        val on = !model.holdsSnapshot
        if (on != autosaveOn) {
            autosaveOn = on
            setAutosave(on)
        }
    }

    /** While a window fills the editor the dock nodes stay alive but are not drawn. */
    fun dockSpaceFlags(): Int =
        if (model.fullEditorWindow != null) ImGuiDockNodeFlags.KeepAliveOnly else ImGuiDockNodeFlags.None

    fun afterDockSpace(central: ImGuiDockNode) {
        DockLayoutFlags.applyCentral(central)
        if (pendingDocks.isEmpty() || !central.isValidPtr) return
        pendingDocks.forEach { name -> windowOf(name)?.let { Internal.dockBuilderDockWindow(titleOf(it, it.name), central.id) } }
        pendingDocks.clear()
    }

    /** [playing] is the game's Play state this frame. */
    fun afterWindows(playing: Boolean) {
        pendingFocus.forEach { name ->
            val window = windowOf(name)?.takeIf { it.isOpen.get() } ?: return@forEach
            val id = if (model.fullEditorWindow == name) name + MaximizeSession.HOST_ID_SUFFIX else name
            ImGui.setWindowFocus(titleOf(window, id))
        }
        pendingFocus.clear()

        MaximizeSession.apply(model, MaximizeSession.drain())
        model.window?.let { if (windowOf(it)?.isOpen?.get() != true) model.onWindowClosed(it) }
        if (playing && !wasPlaying) model.onPlay(MaximizeSession.VIEWPORT_WINDOW)
        wasPlaying = playing
    }

    /** Reset Layout: drop the state and anything queued; the layout is rebuilt anyway. */
    fun onResetLayout() {
        model.onResetLayout()
        pendingReload = null
        pendingDocks.clear()
        pendingFocus.clear()
    }

    /**
     * The editor is closing. Returns the ini text to save instead of the layout on screen (the layout from before
     * maximizing over the viewport), or null to save the layout as usual. Autosave stays off in the first case, so
     * ImGui's own save at shutdown does not overwrite it.
     */
    fun onExit(): String? {
        pendingReload = null
        pendingDocks.clear()
        pendingFocus.clear()
        return model.onExit()
    }
}
