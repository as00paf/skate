package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.AsyncCommand
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction.OpenFailed
import com.pafoid.skate.engine.events.SceneAction.OpenSucceeded
import com.pafoid.skate.engine.utils.IJobSystem
import kotlinx.coroutines.Job
import java.io.File

class OpenSceneFileCommand(
    private val scenePath: String,
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
                val sceneName = File(scenePath).nameWithoutExtension.ifBlank { "Loaded Scene" }
                val loadedScene = sceneFactory(sceneName)
                loadedScene.init()

                if (sceneSerializer.loadFromFile(loadedScene, scenePath)) {
                    sceneManager.openSceneBlocking(loadedScene)
                    openedScene = loadedScene
                    eventSystem.publish(OpenSucceeded(loadedScene))
                    completedSuccessfully = true
                } else {
                    loadedScene.destroyScene()
                    eventSystem.publish(OpenFailed("Could not load scene from file: $scenePath"))
                }
            }.onFailure {
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

    override fun getDisplayName(): String = "Open Scene File"
    override fun getTargetName(): String? = File(scenePath).name
}
