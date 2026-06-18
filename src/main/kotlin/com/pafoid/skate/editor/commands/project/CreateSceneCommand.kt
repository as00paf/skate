package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.AsyncCommand
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction
import kotlinx.coroutines.Job

class CreateSceneCommand(
    private val name: String,
    private val filePath: String,
    private val eventSystem: EventSystem,
    private val sceneManager: SceneManager
) : AsyncCommand {

    private var createdScene: Scene? = null
    private var completedSuccessfully: Boolean = false
    private var completionJob: Job? = null

    override fun execute() {
        createdScene = sceneManager.createNewScene(name, filePath)
        completedSuccessfully = createdScene != null
        createdScene?.let {
            eventSystem.publish(SceneAction.Created(it))
        }
    }

    override fun undo() {
        createdScene?.let { eventSystem.publish(SceneAction.DeleteRequested(it)) }
    }

    override fun getCompletionJob(): Job? = completionJob

    override fun didCompleteSuccessfully(): Boolean = completedSuccessfully

    override fun shouldPushToHistoryOnSuccess(): Boolean = false

    override fun getDisplayName(): String = "Create Scene"
    override fun getTargetName(): String? = name
}
