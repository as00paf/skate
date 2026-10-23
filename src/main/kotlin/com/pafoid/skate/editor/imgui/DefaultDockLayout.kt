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
        "window.environment" to Zone.RIGHT,
        "window.audio_inspector" to Zone.RIGHT,
        "window.asset_browser" to Zone.BOTTOM,
        "window.console" to Zone.BOTTOM,
        "window.profiler" to Zone.BOTTOM,
        "window.command_history" to Zone.BOTTOM,
        "window.render_graph" to Zone.BOTTOM,
        "window.physics_tuner" to Zone.BOTTOM,
        "window.input_testing" to Zone.BOTTOM,
    )

    /** Dialogs, popups and settings windows have no zone and stay floating. */
    fun zoneOf(windowName: String): Zone? = zones[windowName]
}
