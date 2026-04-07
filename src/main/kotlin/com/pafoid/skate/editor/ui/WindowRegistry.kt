package com.pafoid.skate.editor.ui

import com.pafoid.skate.editor.imgui.data.EditorWindow
import com.pafoid.skate.editor.windows.AssetBrowserWindow
import com.pafoid.skate.editor.windows.AudioInspectorWindow
import com.pafoid.skate.editor.windows.CommandHistoryWindow
import com.pafoid.skate.editor.windows.ConsoleWindow
import com.pafoid.skate.editor.windows.EditorSettingsWindow
import com.pafoid.skate.editor.windows.EnvironmentWindow
import com.pafoid.skate.editor.windows.GameViewWindow
import com.pafoid.skate.editor.windows.InputTestingWindow
import com.pafoid.skate.editor.windows.KeyBindingsWindow
import com.pafoid.skate.editor.windows.PhysicsTunerWindow
import com.pafoid.skate.editor.windows.ProfilerWindow
import com.pafoid.skate.editor.windows.ProjectSettingsWindow
import com.pafoid.skate.editor.windows.ProjectSwitcherDialog
import com.pafoid.skate.editor.windows.ProjectWindow
import com.pafoid.skate.editor.windows.ProjectWizardWindow
import com.pafoid.skate.editor.windows.PropertiesWindow
import com.pafoid.skate.editor.windows.RenderGraphWindow
import com.pafoid.skate.editor.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.windows.SearchEverywhereWindow
import com.pafoid.skate.editor.windows.SystemsWindow
import imgui.type.ImBoolean

/**
 * Registry of all dockable editor windows.
 *
 * Centralizes window management for rendering, menus, and dock layout.
 * All windows are created via dependency injection and registered here.
 */
class WindowRegistry(
    val hierarchyWindow: SceneHierarchyWindow,
    val propertiesWindow: PropertiesWindow,
    val gameViewWindow: GameViewWindow,
    val assetBrowser: AssetBrowserWindow,
    val environmentWindow: EnvironmentWindow,
    val profilerWindow: ProfilerWindow,
    val consoleWindow: ConsoleWindow,
    val physicsTunerWindow: PhysicsTunerWindow,
    val inputTestingWindow: InputTestingWindow,
    val systemsWindow: SystemsWindow,
    val editorSettingsWindow: EditorSettingsWindow,
    val projectSettingsWindow: ProjectSettingsWindow,
    val keyBindingsWindow: KeyBindingsWindow,
    val commandHistoryWindow: CommandHistoryWindow,
    val renderGraphWindow: RenderGraphWindow,
    val searchEverywhereWindow: SearchEverywhereWindow,
    val projectWizardWindow: ProjectWizardWindow,
    val projectSwitcherDialog: ProjectSwitcherDialog,
    val audioInspectorWindow: AudioInspectorWindow,
    val projectWindow: ProjectWindow
) {

    /**
     * List of all dockable editor windows with their metadata.
     * Modal/overlay windows (projectWizard, projectSwitcher, searchEverywhere)
     * are accessed directly via val properties and are NOT in this list.
     */
    val windows: List<EditorWindow> = listOf(
        EditorWindow("window.hierarchy", hierarchyWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.properties", propertiesWindow, ImBoolean(true)),
        EditorWindow("window.game_viewport", gameViewWindow, ImBoolean(true)),
        EditorWindow("window.asset_browser", assetBrowser, ImBoolean(true)),
        EditorWindow("window.environment", environmentWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.profiler", profilerWindow, ImBoolean(true)),
        EditorWindow("window.console", consoleWindow, ImBoolean(true)),
        EditorWindow("window.physics_tuner", physicsTunerWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.input_testing", inputTestingWindow, ImBoolean(false)),
        EditorWindow("window.systems", systemsWindow, ImBoolean(true), requiresScene = true),
        EditorWindow("window.command_history", commandHistoryWindow, ImBoolean(true)),
        EditorWindow("window.render_graph", renderGraphWindow, ImBoolean(false)),
        EditorWindow("window.editor_settings", editorSettingsWindow, ImBoolean(false)),
        EditorWindow("window.project_settings", projectSettingsWindow, ImBoolean(false)),
        EditorWindow("window.keybindings", keyBindingsWindow, ImBoolean(false)),
        EditorWindow("window.audio_inspector", audioInspectorWindow, ImBoolean(false), requiresScene = true),
        EditorWindow("window.project", projectWindow, ImBoolean(false))
    )

    /**
     * Get a window by its name key.
     *
     * @param key The window name key (e.g., "window.hierarchy")
     * @return The window instance or null if not found
     */
    fun getWindow(key: String): Any? {
        return windows.find { it.nameKey == key }?.instance
    }

    /**
     * Get a window by its type.
     *
     * @param T The window type
     * @return The window instance or null if not found
     */
    inline fun <reified T> getWindow(): T? {
        return windows.find { it.instance is T }?.instance as? T
    }

    /**
     * Hide all project-specific windows (set showFlag to false).
     * Called when a project is closed.
     */
    fun hideAllWindows() {
        windows.forEach { it.showFlag.set(false) }
    }

    /**
     * Show default editor windows for a new or loaded project.
     * Only shows windows that were originally visible by default.
     */
    fun showDefaultWindows() {
        val defaultVisible = setOf(
            "window.hierarchy",
            "window.properties",
            "window.game_viewport",
            "window.asset_browser",
            "window.environment",
            "window.profiler",
            "window.console",
            "window.physics_tuner",
            "window.systems",
            "window.command_history",
            "window.project",
            "window.audio_inspector",
            "window.render_graph",
        )

        windows.forEach {
            it.showFlag.set(it.nameKey in defaultVisible)
        }
    }
}
