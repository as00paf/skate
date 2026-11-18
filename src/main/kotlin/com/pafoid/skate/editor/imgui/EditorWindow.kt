package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.help.HelpRouter
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean

abstract class EditorWindow(
    val name: String,
    val isDefault: Boolean = false,
    val isOpen: ImBoolean = ImBoolean(false)
) {
    abstract fun imgui()

    /**
     * Called once when the window goes from open to closed by any path (title-bar X, View > Windows, Hide All, code).
     * Closed windows get no [imgui] call, so per-session state (previews, drafts) must be dropped here.
     */
    open fun onClosed() {}

    /** The icon in front of the title (EDT-51 §2.2). Null for windows not converted yet (EDT-52). */
    open val icon: String? = null

    /** Windows the Help window offers to open below this window's article ("Related windows"), by [name]. */
    open val relatedWindows: List<String> = emptyList()

    /** Help topic: the article `editor/help/<locale>/<helpId>.md` (EDT-53, EDT-68); `window.console` -> `console`. */
    val helpId: String get() = helpIdOf(name)

    /** Opens the Help window on this window's article (the `?` and the tab menu's Help, EDT-68). */
    fun openHelp() = HelpRouter.open(helpId)

    /** Set by the tab menu's Reset position; applied before the next `begin`. */
    internal var resetPositionRequested = false

    /** EDT-84: a dock node to move into before the next `begin` (a side-bar button moved to another side). */
    internal var dockRequest: Int? = null

    /** EDT-101: from the last `beginWindow`: whether the window has a title bar and a close button there. */
    internal var hasTitleBar: Boolean = true
    internal var hasCloseButton: Boolean = true

    /** EDT-101: the title the last `beginWindow` used (the full-editor host has its own ID). */
    internal var lastTitle: String? = null

    /** EDT-101: the corner buttons drawn this frame, painted over the content by [WindowHeader.drawCornerOverlay]. */
    internal var cornerOverlay: WindowHeader.CornerOverlay? = null

    /** Dialogs float: no dock to reset to. */
    open val isDialog: Boolean get() = false

    /** Every docked standard window can be maximized, except the Game Viewport (EDT-88 Q7). */
    open val canMaximize: Boolean get() = MaximizeSession.canMaximize(this)

    /**
     * Window title: the [icon], then translated text (of [textKey], [name] by default) with [name] as its stable
     * ImGui ID, so docking, imgui.ini and the open state survive a language change. Every `begin` of this window must
     * use it.
     */
    fun title(strings: StringManager, prefix: String = "", textKey: String = name, id: String = name): String =
        ImGuiIds.stable(prefix + (icon?.let { "$it " } ?: "") + strings.getString(textKey), id)

    /**
     * The standard `begin` (EDT-51): the title with its icon and the tab / title-bar right-click menu (Help, Close,
     * Reset position). As with `ImGui.begin`, call `ImGui.end()` whatever this returns.
     * Draw [header] first in the content.
     */
    protected fun beginWindow(strings: StringManager, flags: Int = 0, closable: Boolean = true): Boolean {
        // EDT-88: maximized over the whole editor, the window is drawn in a host over the dock space under its own
        // ID, so its dock slot and the saved layout are not touched.
        val host = MaximizeSession.fullEditorArea(name)
        var windowFlags = flags
        val title = if (host != null) {
            ImGui.setNextWindowPos(host.x, host.y, ImGuiCond.Always)
            ImGui.setNextWindowSize(host.width, host.height, ImGuiCond.Always)
            windowFlags = windowFlags or HOST_FLAGS
            title(strings, id = name + MaximizeSession.HOST_ID_SUFFIX)
        } else {
            if (resetPositionRequested) {
                resetPositionRequested = false
                DefaultDockLayout.docks.resetTarget(name)?.let { ImGui.setNextWindowDockID(it) }
            }
            dockRequest?.let {
                dockRequest = null
                ImGui.setNextWindowDockID(it)
            }
            title(strings)
        }
        hasTitleBar = windowFlags and ImGuiWindowFlags.NoTitleBar == 0
        hasCloseButton = closable
        lastTitle = title
        val visible = if (closable) ImGui.begin(title, isOpen, windowFlags) else ImGui.begin(title, windowFlags)
        if (!isDialog && host == null) DefaultDockLayout.docks.record(name, ImGui.getWindowDockID())
        // A window with a horizontal scrollbar can reach its overflow: only the others report it.
        if (windowFlags and ImGuiWindowFlags.HorizontalScrollbar == 0) InspectorLayout.windowProbe?.invoke(name, ImGui.getScrollMaxX())
        val focused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows)
        ToolWindowStates.report(name, visible, focused) // EDT-84: the side-bar buttons' look and click
        if (focused) lastFocused = name // EDT-101: F1's target
        if (canMaximize) {
            if (focused) MaximizeSession.focused = name
            // Right after `begin` the last item is the tab (docked) or the title bar (the host): double-click toggles.
            // A floating window's title bar keeps ImGui's own double-click (collapse).
            val onTab = host != null || ImGui.isWindowDocked()
            if (onTab && ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                MaximizeSession.request(name)
            }
        }
        WindowHeader.tabMenu(strings, this)
        return visible
    }

    /** The one-line header strip: the window's own [tools] on the left, `?` on the right (EDT-51 §2.2). */
    protected fun header(strings: StringManager, tools: (() -> Unit)? = null) = WindowHeader.render(strings, this, tools)

    companion object {
        /** The last window that had focus (any standard window or dialog): the Help shortcut's target (EDT-101). */
        @Volatile
        var lastFocused: String? = null

        /** `window.console` -> `console`. */
        fun helpIdOf(windowName: String): String = windowName.removePrefix("window.")

        /** The full-editor host: fixed over the dock space, not docked, not saved (docs/design/EDT-88.md §3). */
        const val HOST_FLAGS = ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or
            ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoSavedSettings
    }
}
