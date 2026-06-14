package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.EditorWindow
import com.pafoid.skate.editor.ui.windows.AssetBrowserWindow
import com.pafoid.skate.editor.ui.windows.AudioInspectorWindow
import com.pafoid.skate.editor.ui.windows.CommandHistoryWindow
import com.pafoid.skate.editor.ui.windows.ConsoleWindow
import com.pafoid.skate.editor.ui.windows.EditorSettingsWindow
import com.pafoid.skate.editor.ui.windows.EnvironmentWindow
import com.pafoid.skate.editor.ui.windows.GameViewWindow
import com.pafoid.skate.editor.ui.windows.InputTestingWindow
import com.pafoid.skate.editor.ui.windows.KeyBindingsWindow
import com.pafoid.skate.editor.ui.windows.PhysicsTunerWindow
import com.pafoid.skate.editor.ui.windows.ProfilerWindow
import com.pafoid.skate.editor.ui.windows.ProjectSettingsWindow
import com.pafoid.skate.editor.ui.windows.ProjectSwitcherDialog
import com.pafoid.skate.editor.ui.windows.ProjectWindow
import com.pafoid.skate.editor.ui.windows.ProjectWizardWindow
import com.pafoid.skate.editor.ui.windows.PropertiesWindow
import com.pafoid.skate.editor.ui.windows.RenderGraphWindow
import com.pafoid.skate.editor.ui.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.ui.windows.SearchEverywhereWindow
import com.pafoid.skate.editor.ui.windows.SystemsWindow
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
    val projectWindow: ProjectWindow,
) {
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
        EditorWindow("window.project", projectWindow, ImBoolean(false)),
        EditorWindow("window.project_switcher", projectSwitcherDialog, ImBoolean(false), false),
        EditorWindow("window.project_wizard", projectWizardWindow, ImBoolean(false), false),
        EditorWindow("window.search", searchEverywhereWindow, ImBoolean(false), false),
    )

    fun getWindow(key: String): EditorWindow? {
        return windows.find { it.nameKey == key }
    }

    inline fun <reified T> getWindow(): T? {
        return windows.find { it.instance is T }?.instance as? T
    }

    fun isOpen(key: String): Boolean {
        return getWindow(key)?.showFlag?.get() ?: false
    }

    fun hideAllWindows() {
        windows.forEach { it.showFlag.set(false) }
    }

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