package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.editor.commands.AsyncCommand
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction.OpenFailed
import kotlinx.coroutines.Job
import com.pafoid.skate.editor.vcs.ConflictMarkers
import java.io.File

class OpenSceneFileCommand(
    private val sceneFile: File,
    private val serializer: Serializer,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
    /** The scene decoded off the main thread by the loading screen (EDT-56); null decodes it here. */
    private val decoded: Result<Scene?>? = null,
) : AsyncCommand {
    var openedScene: Scene? = null
        private set

    @Volatile
    private var completedSuccessfully: Boolean = false
    private var completionJob: Job? = null

    override fun execute() {
        completedSuccessfully = false
        openedScene = null

        val scene = (decoded ?: decode(sceneFile, serializer)).getOrElse { e ->
            eventSystem.publish(OpenFailed("${sceneFile.absolutePath}: ${e.message ?: e.javaClass.simpleName}"))
            return
        }
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

    companion object {
        /** Reads and decodes [sceneFile]; touches no scene state, so it can run off the main thread. */
        fun decode(sceneFile: File, serializer: Serializer): Result<Scene?> =
            runCatching {
                // EDT-60: a scene with Git conflict markers is refused with a clear message, not a JSON error
                serializer.decodeFile<Scene?>(FileKind.SCENE, ConflictMarkers.requireClean(sceneFile.name, sceneFile.readText()))
            }
    }
}
