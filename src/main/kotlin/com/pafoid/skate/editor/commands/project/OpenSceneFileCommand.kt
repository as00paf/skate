package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.AsyncCommand
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction.OpenFailed
import kotlinx.coroutines.Job
import java.io.File

class OpenSceneFileCommand(
    private val sceneFile: File,
    private val serializer: Serializer,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) : AsyncCommand {
    var openedScene: Scene? = null
        private set

    @Volatile
    private var completedSuccessfully: Boolean = false
    private var completionJob: Job? = null

    override fun execute() {
        completedSuccessfully = false
        openedScene = null

        val scene = serializer.decode<Scene?>(sceneFile.readText())
        if (scene != null) {
            sceneManager.openScene(scene)
            openedScene = scene
            completedSuccessfully = true
        } else {
            eventSystem.publish(OpenFailed("Could not load scene from file: ${sceneFile.absolutePath}"))
        }
    }

    override fun undo() {
        openedScene?.let { sceneManager.closeScene(it) }
    }

    override fun getCompletionJob(): Job? = completionJob

    override fun didCompleteSuccessfully(): Boolean = completedSuccessfully

    override fun shouldPushToHistoryOnSuccess(): Boolean = false

    override fun getDisplayName(): String = "Open Scene File"
    override fun getTargetName(): String? = sceneFile.name
}
