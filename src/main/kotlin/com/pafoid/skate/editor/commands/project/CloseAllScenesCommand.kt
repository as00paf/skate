package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction

class CloseAllScenesCommand(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) : ExecuteOnlyCommand {

    private var openedScenes: List<Scene>? = null

    override fun execute() {
        openedScenes = sceneManager.openScenes
        sceneManager.closeAllScenes()
    }

    override fun undo() {
        eventSystem.publish(SceneAction.ReopenAllRequested(openedScenes.orEmpty()))
    }

    override fun getDisplayName(): String = "Close All Scenes"
    override fun getTargetName(): String? = null
}
