package com.pafoid.skate.editor.imgui

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.imgui.data.EditorWindow
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
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
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import imgui.type.ImBoolean

/**
 * Registry of all dockable editor windows.
 *
 * Centralizes window management for rendering, menus, and dock layout.
 * All windows are created and registered here.
 */
class WindowRegistry(
    engine: Engine,
    editor: Editor,
    projectManager: ProjectManager,
    settingsManager: SettingsManager,
    stringManager: StringManager
) {
    val hierarchyWindow =
        SceneHierarchyWindow(engine, stringManager, editor.clipboardService, engine.logger, engine.eventSystem)
    val propertiesWindow = PropertiesWindow(stringManager, engine, engine.eventSystem, engine.logger)
    val gameViewWindow = GameViewWindow(engine, editor, settingsManager, stringManager)
    val assetBrowser = AssetBrowserWindow(engine, stringManager, engine.prefabsGenerator, editor.undoRedoManager)
    val environmentWindow = EnvironmentWindow(stringManager, engine.eventSystem, engine.systemManager)
    val profilerWindow = ProfilerWindow(stringManager)
    val consoleWindow = ConsoleWindow(engine.logger, stringManager, engine.eventSystem)
    val physicsTunerWindow = PhysicsTunerWindow(stringManager, engine)
    val inputTestingWindow = InputTestingWindow(engine, stringManager)
    val systemsWindow = SystemsWindow(stringManager, engine)
    val editorSettingsWindow = EditorSettingsWindow(settingsManager, stringManager)
    val projectSettingsWindow =
        ProjectSettingsWindow(settingsManager, stringManager, projectManager, engine.eventSystem)
    val keyBindingsWindow = KeyBindingsWindow(settingsManager, stringManager)
    val commandHistoryWindow = CommandHistoryWindow(editor.undoRedoManager, stringManager, engine.eventSystem)
    val renderGraphWindow = RenderGraphWindow(stringManager, engine)
    val searchEverywhereWindow = SearchEverywhereWindow(engine, stringManager)
    val projectWizardWindow = ProjectWizardWindow(engine.logger, stringManager, engine.eventSystem)
    val projectSwitcherDialog = ProjectSwitcherDialog(projectManager, stringManager, engine.eventSystem)
    val audioInspectorWindow = AudioInspectorWindow(stringManager)
    val projectWindow =
        ProjectWindow(stringManager, engine.logger, projectManager, engine.eventSystem, engine.serializer)

    val windows: List<EditorWindow> = listOf(
        EditorWindow("window.hierarchy", hierarchyWindow, requiresScene = true),
        EditorWindow("window.properties", propertiesWindow, ImBoolean(false)),
        EditorWindow("window.game_viewport", gameViewWindow, ImBoolean(false)),
        EditorWindow("window.asset_browser", assetBrowser, ImBoolean(false)),
        EditorWindow("window.environment", environmentWindow, requiresScene = true),
        EditorWindow("window.profiler", profilerWindow, ImBoolean(false)),
        EditorWindow("window.console", consoleWindow, ImBoolean(false)),
        EditorWindow("window.physics_tuner", physicsTunerWindow, requiresScene = true),
        EditorWindow("window.input_testing", inputTestingWindow, ImBoolean(false)),
        EditorWindow("window.systems", systemsWindow, requiresScene = true),
        EditorWindow("window.command_history", commandHistoryWindow, ImBoolean(false)),
        EditorWindow("window.render_graph", renderGraphWindow, ImBoolean(false)),
        EditorWindow("window.editor_settings", editorSettingsWindow, ImBoolean(false)),
        EditorWindow("window.project_settings", projectSettingsWindow, ImBoolean(false)),
        EditorWindow("window.keybindings", keyBindingsWindow, ImBoolean(false)),
        EditorWindow("window.audio_inspector", audioInspectorWindow, requiresScene = true),
        EditorWindow("window.project", projectWindow, ImBoolean(false)),
        EditorWindow("window.project_switcher", projectSwitcherDialog, requiresScene = false),
        EditorWindow("window.project_wizard", projectWizardWindow, requiresScene = false),
        EditorWindow("window.search", searchEverywhereWindow, requiresScene = false),
    )

    fun getWindow(key: String): EditorWindow? {
        return windows.find { it.nameKey == key }
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