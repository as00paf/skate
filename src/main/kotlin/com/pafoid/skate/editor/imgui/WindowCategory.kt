package com.pafoid.skate.editor.imgui

/**
 * Grouping of windows in View > Windows. Windows without a category (dialogs, transient popups) are not listed: the
 * settings and Key Bindings dialogs open from the gear and Settings menus (EDT-52E, EDT-64).
 */
enum class WindowCategory(val labelKey: String) {
    SCENE("menu.view.windows.scene"),
    ASSETS("menu.view.windows.assets"),
    DEBUG("menu.view.windows.debug"),
    SCRIPTING("menu.view.windows.scripting"),
    AUDIO("menu.view.windows.audio"),
    HELP("menu.view.windows.help"),
    PROJECT("menu.view.windows.project");

    companion object {
        private val byWindow = mapOf(
            "window.hierarchy" to SCENE,
            "window.game_viewport" to SCENE,
            "window.properties" to SCENE,
            "window.project" to SCENE,
            "window.asset_browser" to ASSETS,
            "window.asset_store" to ASSETS,
            "window.video_to_animation" to ASSETS,
            "window.animation_editor" to ASSETS,
            "window.animator" to ASSETS,
            "window.console" to DEBUG,
            "window.profiler" to DEBUG,
            "window.command_history" to DEBUG,
            "window.render_graph" to DEBUG,
            "window.shell" to DEBUG,
            "window.script_editor" to SCRIPTING,
            "window.audio_inspector" to AUDIO,
            "window.help" to HELP,
            "window.version_control" to PROJECT,
        )

        fun of(window: EditorWindow): WindowCategory? = of(window.name)

        fun of(windowName: String): WindowCategory? = byWindow[windowName]

        /** Dockable windows grouped by category in declaration order; uncategorised windows are omitted. */
        fun grouped(windows: List<EditorWindow>): Map<WindowCategory, List<EditorWindow>> =
            windows.filter { it !is EditorDialog }.groupBy { of(it) }.filterKeys { it != null }
                .mapKeys { it.key!! }.toSortedMap()
    }
}
