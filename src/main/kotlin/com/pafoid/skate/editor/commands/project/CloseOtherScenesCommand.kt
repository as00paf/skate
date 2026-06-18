package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction

class CloseOtherScenesCommand(
    private val scene: Scene,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) : ExecuteOnlyCommand {

    private var otherScenes: List<Scene>? = null

    override fun execute() {
        otherScenes = sceneManager.openScenes.filter { it != scene }
        sceneManager.closeOtherScenes(scene)
    }

    override fun undo() {
        eventSystem.publish(SceneAction.CloseRequested(scene))
        eventSystem.publish(SceneAction.ReopenAllRequested(otherScenes.orEmpty()))
    }

    override fun getDisplayName(): String = "Close Other Scenes"
    override fun getTargetName(): String? = scene?.name
}
