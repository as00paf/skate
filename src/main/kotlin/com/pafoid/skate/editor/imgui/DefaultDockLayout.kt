package com.pafoid.skate.editor.imgui

/** Default dock zones and split ratios used on first run and by Reset Layout. */
object DefaultDockLayout {
    enum class Zone { LEFT, CENTER, RIGHT, BOTTOM }

    const val LEFT_RATIO = 0.18f
    const val RIGHT_RATIO = 0.22f
    const val BOTTOM_RATIO = 0.28f

    private val zones: Map<String, Zone> = linkedMapOf(
        "window.hierarchy" to Zone.LEFT,
        "window.project" to Zone.LEFT,
        "window.game_viewport" to Zone.CENTER,
        "window.properties" to Zone.RIGHT,
        "window.audio_inspector" to Zone.RIGHT,
        "window.asset_browser" to Zone.BOTTOM,
        "window.asset_store" to Zone.BOTTOM,
        "window.video_to_animation" to Zone.BOTTOM,
        // EDT-55: open by default as a bottom tab; the centre node has no tab bar, so a window docked there hides
        // behind the viewport.
        "window.script_editor" to Zone.BOTTOM,
        "window.console" to Zone.BOTTOM,
        "window.profiler" to Zone.BOTTOM,
        "window.command_history" to Zone.BOTTOM,
        "window.render_graph" to Zone.BOTTOM,
        "window.shell" to Zone.BOTTOM,
        // EDT-68: next to the Console when first opened.
        "window.help" to Zone.BOTTOM,
    )

    /**
     * The tab shown in front of its zone after the layout is built (first run, Reset Layout). Other default windows of
     * the zone, such as the Render Graph (EDT-54), join as background tabs.
     */
    val ACTIVE_TABS = listOf("window.console")

    /** Dialogs, popups and settings windows have no zone and stay floating. */
    fun zoneOf(windowName: String): Zone? = zones[windowName]

    /** Windows that share [zone] in the default layout. */
    fun windowsIn(zone: Zone): List<String> = zones.filterValues { it == zone }.keys.toList()

    /** Where each window was last docked, for the tab menu's Reset position (EDT-51 §2.3). */
    val docks = DockMemory()

    /**
     * The dock node each window was seen in. Windows report it every frame from inside `begin`, so this needs no
     * access to the layout builder. Reset position docks a window back with a window of its default zone, or, when
     * none of those is docked, into the first node the window itself was docked in.
     */
    class DockMemory {
        private val current = HashMap<String, Int>()
        private val home = HashMap<String, Int>()

        /** [dockId] is ImGui's dock id of [window] this frame; 0 = floating. */
        fun record(window: String, dockId: Int) {
            if (dockId == 0) {
                current.remove(window)
                return
            }
            current[window] = dockId
            home.putIfAbsent(window, dockId)
        }

        /** The dock id to put [window] back into, or null when it has no default zone or nowhere is known. */
        fun resetTarget(window: String): Int? {
            val zone = zoneOf(window) ?: return null
            return windowsIn(zone).filter { it != window }.firstNotNullOfOrNull { current[it] } ?: home[window]
        }

        fun clear() {
            current.clear()
            home.clear()
        }
    }
}
