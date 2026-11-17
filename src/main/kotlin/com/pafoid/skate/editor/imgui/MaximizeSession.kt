package com.pafoid.skate.editor.imgui

/**
 * What the window headers and `ImGuiLayer` share about maximizing (EDT-88), like [DefaultDockLayout.docks]. Windows
 * only read the state and queue requests; the layer applies the requests once per frame, after the windows drew, so
 * the layout never changes in the middle of a frame.
 */
object MaximizeSession {
    /** Suffix of the full-editor host's ImGui ID: `<title>###<window>.maximized` (docs/design/EDT-88.md §3). */
    const val HOST_ID_SUFFIX = ".maximized"

    /** The Game Viewport: the target of "over the viewport", never maximized itself (it has F12). */
    const val VIEWPORT_WINDOW = "window.game_viewport"

    /** A request from a header button, tab menu, double-click, shortcut or the status bar. */
    data class Request(val window: String?, val mode: MaximizeMode?) {
        val isRestore: Boolean get() = window == null
    }

    /** A screen rectangle. */
    data class Area(val x: Float, val y: Float, val width: Float, val height: Float)

    /** The live model; set by `ImGuiLayer`. Null (nothing maximized) in tests and before start-up. */
    @Volatile
    var model: WindowMaximize? = null

    /** The mode of the button, double-click and shortcut (Editor Settings › Interface). */
    @Volatile
    var defaultMode: () -> MaximizeMode = { MaximizeMode.FULL_EDITOR }

    /** The dock space's area this frame: the full-editor host fills it. */
    @Volatile
    var hostArea: Area? = null

    /** The last standard window that had focus: the shortcut's target. */
    @Volatile
    var focused: String? = null

    private val requests = ArrayList<Request>()

    /**
     * The docked tool windows (those with a default dock zone), except the Game Viewport (EDT-88 Q7). Dialogs and
     * floating helpers such as Search Everywhere are not.
     */
    fun canMaximize(window: EditorWindow): Boolean =
        !window.isDialog && window.name != VIEWPORT_WINDOW && DefaultDockLayout.zoneOf(window.name) != null

    fun isMaximized(window: String): Boolean = model?.isMaximized(window) == true

    /**
     * §3: while [full] fills the editor only it and the floating dialogs draw; the docked windows keep their place in
     * the (undrawn) dock space.
     */
    fun drawsWhileMaximized(window: EditorWindow, full: String?): Boolean =
        full == null || window.name == full || window.isDialog

    /** [hostArea] when [window] is the one drawn over the whole editor, else null. */
    fun fullEditorArea(window: String): Area? = if (model?.fullEditorWindow == window) hostArea else null

    /** Maximizes [window] (or restores it when it is the maximized one). */
    @Synchronized
    fun request(window: String, mode: MaximizeMode = defaultMode()) {
        requests += Request(window, mode)
    }

    @Synchronized
    fun requestRestore() {
        requests += Request(null, null)
    }

    /** The queued requests, oldest first; empties the queue. */
    @Synchronized
    fun drain(): List<Request> = requests.toList().also { requests.clear() }

    /** Applies [requests] to [model]. */
    fun apply(model: WindowMaximize, requests: List<Request>) {
        for (r in requests) {
            if (r.isRestore) model.restore() else model.toggle(r.window!!, r.mode ?: defaultMode())
        }
    }

    /** Back to the start-up state (tests, editor shutdown). */
    @Synchronized
    fun reset() {
        model = null
        hostArea = null
        focused = null
        requests.clear()
        defaultMode = { MaximizeMode.FULL_EDITOR }
    }
}
