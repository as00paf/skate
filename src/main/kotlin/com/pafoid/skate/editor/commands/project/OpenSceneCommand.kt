package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.AsyncCommand
import com.pafoid.skate.editor.data.SceneOpenResult
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction.OpenCancelled
import com.pafoid.skate.engine.events.SceneAction.OpenFailed
import com.pafoid.skate.engine.events.SceneAction.OpenSucceeded
import com.pafoid.skate.engine.utils.IJobSystem
import kotlinx.coroutines.Job

class OpenSceneCommand(
    private val sceneSerializer: SceneSerializer,
    private val sceneManager: SceneManager,
    private val jobSystem: IJobSystem,
    private val eventSystem: EventSystem,
    private val sceneFactory: (String) -> Scene = { name ->
        Scene(name)
    }
) : AsyncCommand {
    var openedScene: Scene? = null
        private set
    @Volatile
    private var completedSuccessfully: Boolean = false
    private var completionJob: Job? = null

    override fun execute() {
        completedSuccessfully = false
        openedScene = null
        completionJob = jobSystem.runOnMain {
            runCatching {
                val loadedScene = sceneFactory("Loaded Scene")
                loadedScene.init()
                when (val openResult = sceneSerializer.open(loadedScene)) {
                    is SceneOpenResult.Loaded -> {
                        sceneManager.openSceneBlocking(loadedScene)
                        openedScene = loadedScene
                        eventSystem.publish(OpenSucceeded(loadedScene))
                        completedSuccessfully = true
                    }

                    SceneOpenResult.Cancelled -> {
                        loadedScene.destroyScene()
                        openedScene = null
                        eventSystem.publish(OpenCancelled)
                        completedSuccessfully = false
                    }

                    is SceneOpenResult.Failed -> {
                        loadedScene.destroyScene()
                        openedScene = null
                        eventSystem.publish(OpenFailed(openResult.reason))
                        completedSuccessfully = false
                    }
                }
            }.onFailure {
                openedScene = null
                completedSuccessfully = false
                val reason = it.message ?: "Unknown scene open error"
                eventSystem.publish(OpenFailed(reason))
            }
        }
    }

    override fun undo() {
        // Open operations are not reversible — scene stack restoration is complex
    }

    override fun getCompletionJob(): Job? = completionJob

    override fun didCompleteSuccessfully(): Boolean = completedSuccessfully

    override fun shouldPushToHistoryOnSuccess(): Boolean = false

    override fun getDisplayName(): String = "Open Scene"
    override fun getTargetName(): String? = openedScene?.name
}
