package com.pafoid.skate.app

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.Window

class EditorScreen(private val window: Window, engine: Engine) {

    private val settingsManager = SettingsManager(engine.serializer, engine.logger, engine.stringManager)
    private val projectManager = ProjectManager(engine, settingsManager)

    private val editor = Editor(engine, projectManager)

    fun init() {
        settingsManager.load()
        editor.init(window)

        projectManager.init()
    }

    fun update(dt: Float) {
        editor.update(dt)
    }

    fun destroy() {
        editor.destroy()
    }
}