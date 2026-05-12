package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.data.SceneOpenResult
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneOpenCancelled
import com.pafoid.skate.engine.events.SceneOpenFailed
import com.pafoid.skate.engine.events.SceneOpenSucceeded
import com.pafoid.skate.engine.utils.IJobSystem

class OpenSceneCommand(
    private val sceneInitializer: LevelEditorSceneInitializer,
    private val sceneSerializer: SceneSerializer,
    private val sceneManager: SceneManager,
    private val jobSystem: IJobSystem,
    private val eventSystem: EventSystem,
    private val sceneFactory: (String, LevelEditorSceneInitializer) -> Scene = { name, initializer ->
        Scene(name, initializer)
    }
) : Command {
    var openedScene: Scene? = null
        private set

    override fun execute() {
        jobSystem.runOnMain {
            val loadedScene = sceneFactory("Loaded Scene", sceneInitializer)
            loadedScene.init()
            when (val openResult = sceneSerializer.open(loadedScene)) {
                is SceneOpenResult.Loaded -> {
                    sceneManager.openSceneBlocking(loadedScene)
                    openedScene = loadedScene
                    eventSystem.publish(SceneOpenSucceeded(loadedScene))
                }

                SceneOpenResult.Cancelled -> {
                    loadedScene.destroyScene()
                    openedScene = null
                    eventSystem.publish(SceneOpenCancelled)
                }

                is SceneOpenResult.Failed -> {
                    loadedScene.destroyScene()
                    openedScene = null
                    eventSystem.publish(SceneOpenFailed(openResult.reason))
                }
            }
        }
    }

    override fun undo() {
        // Open operations are not reversible — scene stack restoration is complex
    }

    override fun getDisplayName(): String = "Open Scene"
    override fun getTargetName(): String? = openedScene?.name
}