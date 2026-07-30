package com.pafoid.skate.app

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window
import java.io.File

class EditorScreen(private val window: Window, private val engine: Engine) {

    private val projectManager = ProjectManager(engine)

    private val editor = Editor(engine, projectManager)

    fun init() {
        editor.init(window)
        if (!loadLastProject()) {
            engine.eventSystem.publish(WindowAction.Show("window.project_wizard"))
        } else {
            engine.eventSystem.publish(WindowAction.ShowDefault)
        }
    }

    fun loadLastProject(): Boolean {
        val recent = editor.settingsManager.recentProjects.firstOrNull() ?: return false
        val projectFile = File(recent.projectPath)
        if (!projectFile.exists()) return false
        return projectManager.openProjectFile(projectFile)
    }

    fun update(dt: Float) {
        editor.update(dt)
    }

    fun destroy() {
        editor.destroy()
    }
}