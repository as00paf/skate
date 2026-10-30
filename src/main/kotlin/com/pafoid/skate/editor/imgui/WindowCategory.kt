package com.pafoid.skate.editor.imgui

/** Grouping of windows in View > Windows. Windows without a category (transient popups) are not listed. */
enum class WindowCategory(val labelKey: String) {
    SCENE("menu.view.windows.scene"),
    ASSETS("menu.view.windows.assets"),
    DEBUG("menu.view.windows.debug"),
    INPUT("menu.view.windows.input"),
    SCRIPTING("menu.view.windows.scripting"),
    AUDIO("menu.view.windows.audio"),
    SETTINGS("menu.view.windows.settings");

    companion object {
        private val byWindow = mapOf(
            "window.hierarchy" to SCENE,
            "window.game_viewport" to SCENE,
            "window.properties" to SCENE,
            "window.project" to SCENE,
            "window.asset_browser" to ASSETS,
            "window.console" to DEBUG,
            "window.profiler" to DEBUG,
            "window.command_history" to DEBUG,
            "window.render_graph" to DEBUG,
            "window.keybindings" to INPUT,
            "window.script_editor" to SCRIPTING,
            "window.audio_inspector" to AUDIO,
            "window.editor_settings" to SETTINGS,
            "window.project_settings" to SETTINGS,
        )

        fun of(window: EditorWindow): WindowCategory? = byWindow[window.name]

        /** Dockable windows grouped by category in declaration order; uncategorised windows are omitted. */
        fun grouped(windows: List<EditorWindow>): Map<WindowCategory, List<EditorWindow>> =
            windows.filter { it !is EditorDialog }.groupBy { of(it) }.filterKeys { it != null }
                .mapKeys { it.key!! }.toSortedMap()
    }
}
