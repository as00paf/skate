package com.pafoid.skate.editor.imgui

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.windows.AnimationEditorWindow
import com.pafoid.skate.editor.ui.windows.AnimatorWindow
import com.pafoid.skate.editor.ui.windows.AssetBrowserWindow
import com.pafoid.skate.editor.ui.windows.AudioInspectorWindow
import com.pafoid.skate.editor.ui.windows.BlenderImportPanel
import com.pafoid.skate.editor.ui.windows.ConfigFileInspector
import com.pafoid.skate.editor.ui.windows.CommandHistoryWindow
import com.pafoid.skate.editor.ui.windows.ConsoleWindow
import com.pafoid.skate.editor.ui.windows.EditorSettingsWindow
import com.pafoid.skate.editor.ui.windows.ExportWizardWindow
import com.pafoid.skate.editor.ui.windows.VideoToAnimationWindow
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.help.HelpArticleLoader
import com.pafoid.skate.editor.help.HelpArticleSource
import com.pafoid.skate.editor.help.HelpRouter
import com.pafoid.skate.editor.ui.windows.GameViewWindow
import com.pafoid.skate.editor.ui.windows.HelpWindow
import com.pafoid.skate.editor.ui.windows.KeyBindingsWindow
import com.pafoid.skate.editor.ui.windows.ProfilerWindow
import com.pafoid.skate.editor.ui.windows.ProjectSettingsWindow
import com.pafoid.skate.editor.ui.windows.ProjectSwitcherDialog
import com.pafoid.skate.editor.ui.windows.ProjectWindow
import com.pafoid.skate.editor.ui.windows.ProjectWizardWindow
import com.pafoid.skate.editor.ui.windows.PropertiesWindow
import com.pafoid.skate.editor.ui.windows.rendergraph.RenderGraphWindow
import com.pafoid.skate.editor.ui.windows.SceneHierarchyWindow
import com.pafoid.skate.editor.ui.windows.SearchEverywhereWindow
import com.pafoid.skate.editor.ui.windows.ScriptEditorWindow
import com.pafoid.skate.editor.ui.windows.ShellWindow
import com.pafoid.skate.editor.ui.windows.VersionControlWindow
import com.pafoid.skate.editor.ui.windows.vcs.BranchStatusButton
import com.pafoid.skate.editor.ui.windows.vcs.CommitDialog
import com.pafoid.skate.editor.ui.windows.vcs.ConflictsDialog
import com.pafoid.skate.editor.ui.windows.vcs.VcsModals
import com.pafoid.skate.editor.ui.windows.assetStore.AssetStoreWindow
import com.pafoid.skate.engine.core.Engine

class WindowRegistry(
    engine: Engine,
    editor: Editor,
    projectManager: ProjectManager,
) {
    /** The Git confirmations (EDT-79), shared by the branch button, the Commit dialog and the Git log. */
    private val vcsModals = VcsModals(engine.stringManager, engine.eventSystem, editor.vcsSession)

    val windows: List<EditorWindow> = listOf(
        SceneHierarchyWindow(engine, editor.clipboardService, projectManager::hasProject),
        PropertiesWindow(engine, projectManager::hasProject, listOf(BlenderImportPanel(engine.stringManager, editor.blenderSync), ConfigFileInspector(engine.stringManager, editor.undoRedoManager::executeCommand))),
        GameViewWindow(engine, editor, projectManager::hasProject, projectManager::getProjectDirectory),
        AssetBrowserWindow(engine, editor.undoRedoManager),
        ProfilerWindow(engine.stringManager),
        ConsoleWindow(engine),
        EditorSettingsWindow(editor.settingsManager, engine.stringManager, testBlender = editor.blenderSync::test) { engine.eventSystem.publish(com.pafoid.skate.editor.events.WindowAction.Show("window.keybindings")) },
        ProjectSettingsWindow(engine, editor.settingsManager, projectManager),
        KeyBindingsWindow(engine, editor.settingsManager, engine.stringManager, projectManager),
        CommandHistoryWindow(engine, editor.undoRedoManager),
        RenderGraphWindow(engine),
        SearchEverywhereWindow(engine, projectManager::getProjectDirectory),
        ProjectWizardWindow(engine),
        ProjectSwitcherDialog(engine, editor.settingsManager),
        AudioInspectorWindow(engine, projectManager),
        ProjectWindow(engine, projectManager),
        ScriptEditorWindow(engine, projectManager, editor.settingsManager),
        ShellWindow(engine, projectManager),
        VersionControlWindow(engine, projectManager, editor.vcsSession, vcsModals),
        CommitDialog(engine, editor.vcsSession, vcsModals, projectManager::hasProject),
        ConflictsDialog(engine, editor.vcsSession),
        ExportWizardWindow(engine, projectManager, editor.settingsManager),
        AssetStoreWindow(engine, projectManager),
        VideoToAnimationWindow(engine, editor, projectManager),
        AnimationEditorWindow(engine, editor, projectManager),
        AnimatorWindow(engine, editor, projectManager),
        HelpWindow(
            engine.stringManager,
            HelpArticleLoader(HelpArticleSource.default()),
            windowFor = { topic -> getWindow("window.$topic") },
            openWindow = { name -> engine.eventSystem.publish(WindowAction.Show(name)) },
        ),
    )

    val helpWindow: HelpWindow get() = windows.filterIsInstance<HelpWindow>().first()

    /** Windows listed in View > Windows and stored in the layout; dialogs are excluded. */
    val dockableWindows: List<EditorWindow> get() = dockable(windows)

    init {
        // Every `?`, tab-menu Help and Help menu item opens the one Help window (EDT-68).
        HelpRouter.open = { topic -> helpWindow.openTopic(topic) }
        HelpRouter.renderInline = { topic -> helpWindow.renderInline(topic) }
    }

    val menuBar = EditorMenuBar(engine, editor, this, projectManager)
    val statusBar = EditorStatusBar(
        engine,
        BranchStatusButton(
            engine.stringManager, engine.eventSystem, editor.vcsSession, vcsModals,
            hasProject = projectManager::hasProject,
            playing = { engine.runtimePlaying },
            unsavedScenes = { engine.sceneManager.openScenes.any { it.isDirty } },
        ),
        vcsModals,
    )

    fun getWindow(key: String): EditorWindow? {
        return windows.find { it.name == key }
    }

    fun dialog(name: String): EditorDialog? = windows.filterIsInstance<EditorDialog>().find { it.name == name }

    fun hideAllWindows() {
        windows.forEach { it.isOpen.set(false) }
    }

    fun showDefaultWindows() {
        windows.forEach { it.isOpen.set(it.isDefault) }
    }

    companion object {
        fun dockable(windows: List<EditorWindow>): List<EditorWindow> = windows.filter { it !is EditorDialog }
    }
}
