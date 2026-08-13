package com.pafoid.skate.editor.imgui

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.windows.AssetBrowserWindow
import com.pafoid.skate.editor.ui.windows.AudioInspectorWindow
import com.pafoid.skate.editor.ui.windows.CommandHistoryWindow
import com.pafoid.skate.editor.ui.windows.ConsoleWindow
import com.pafoid.skate.editor.ui.windows.EditorSettingsWindow
import com.pafoid.skate.editor.ui.windows.GameViewWindow
import com.pafoid.skate.editor.ui.windows.InputTestingWindow
import com.pafoid.skate.editor.ui.windows.KeyBindingsWindow
import com.pafoid.skate.editor.ui.windows.ProfilerWindow
import com.pafoid.skate.editor.ui.windows.ProjectSettingsWindow
import com.pafoid.skate.editor.ui.windows.ProjectSwitcherDialog
import com.pafoid.skate.editor.ui.windows.ProjectWindow
import com.pafoid.skate.editor.ui.windows.ProjectWizardWindow
import com.pafoid.skate.editor.ui.windows.PropertiesWindow
import com.pafoid.skate.editor.ui.windows.RenderGraphWindow
import com.pafoid.skate.editor.ui.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.ui.windows.SearchEverywhereWindow
import com.pafoid.skate.engine.core.Engine

class WindowRegistry(
    engine: Engine,
    editor: Editor,
    projectManager: ProjectManager,
) {
    val windows: List<EditorWindow> = listOf(
        SceneHierarchyWindow(engine, editor.clipboardService),
        PropertiesWindow(engine),
        GameViewWindow(engine, editor),
        AssetBrowserWindow(engine, editor.undoRedoManager),
        ProfilerWindow(engine.stringManager),
        ConsoleWindow(engine),
        InputTestingWindow(engine),
        EditorSettingsWindow(editor.settingsManager, engine.stringManager),
        ProjectSettingsWindow(engine, editor.settingsManager, projectManager),
        KeyBindingsWindow(editor.settingsManager, engine.stringManager),
        CommandHistoryWindow(engine, editor.undoRedoManager),
        RenderGraphWindow(engine),
        SearchEverywhereWindow(engine),
        ProjectWizardWindow(engine),
        ProjectSwitcherDialog(engine, editor.settingsManager),
        AudioInspectorWindow(engine),
        ProjectWindow(engine, projectManager),
    )

    val menuBar = EditorMenuBar(engine, editor, this, projectManager)
    val statusBar = EditorStatusBar(engine)

    fun getWindow(key: String): EditorWindow? {
        return windows.find { it.name == key }
    }

    fun hideAllWindows() {
        windows.forEach { it.isOpen.set(false) }
    }

    fun showDefaultWindows() {
        windows.forEach { it.isOpen.set(it.isDefault) }
    }
}