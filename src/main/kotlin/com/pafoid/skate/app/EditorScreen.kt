package com.pafoid.skate.app

import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Screen
import com.pafoid.skate.engine.core.Window

class EditorScreen(private val window: Window, private val engine: Engine) : Screen {

    private val projectManager = ProjectManager(engine)

    private val editor = Editor(engine, projectManager)

    fun init() {
        editor.init(window)
        if (!loadLastProject()) {
            engine.eventSystem.publish(WindowAction.Show("window.project_wizard"))
        } else {
            if (!editor.imGuiLayer.restoreWindowState()) {
                engine.eventSystem.publish(WindowAction.ShowDefault)
            }
        }
    }

    fun loadLastProject(): Boolean {
        // EDT-57: "Open the last project on startup" off starts on the project wizard.
        if (!editor.settingsManager.editorSettings.loadLastProjectOnStartup) return false
        val projectFile = editor.settingsManager.lastProjectFile() ?: return false
        if (projectManager.openProjectFile(projectFile)) return true
        // EDT-10: say why instead of silently showing the wizard.
        engine.eventSystem.publish(EditorError("error.open_project.title", projectManager.lastError))
        return false
    }

    override fun update(dt: Float) {
        editor.update(dt)
    }

    override fun destroy() {
        editor.destroy()
    }
}