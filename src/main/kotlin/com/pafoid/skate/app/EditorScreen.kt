package com.pafoid.skate.app

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Screen
import com.pafoid.skate.engine.core.Window
import java.io.File

class EditorScreen(private val window: Window, private val engine: Engine) : Screen {

    private val projectManager = ProjectManager(engine)

    private val editor = Editor(engine, projectManager)

    fun init() {
        editor.init(window)
        if (!loadLastProject()) {
            engine.eventSystem.publish(WindowAction.Show("window.project_wizard"))
        } else {
            engine.eventSystem.publish(WindowAction.ShowDefault)// TODO: fix, should use windows from imgui.ini
        }
    }

    fun loadLastProject(): Boolean {
        val recent = editor.settingsManager.recentProjects.firstOrNull() ?: return false
        val projectFile = File(recent.projectPath)
        if (!projectFile.exists()) return false
        return projectManager.openProjectFile(projectFile)
    }

    override fun update(dt: Float) {
        editor.update(dt)
    }

    override fun destroy() {
        editor.destroy()
    }
}