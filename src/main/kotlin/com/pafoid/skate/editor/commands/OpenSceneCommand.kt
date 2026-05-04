package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import kotlinx.coroutines.runBlocking

class OpenSceneCommand(
    private val sceneInitializer: LevelEditorSceneInitializer,
    private val sceneSerializer: SceneSerializer,
    private val sceneManager: SceneManager
) : Command {
    var openedScene: Scene? = null
        private set

    override fun execute() {
        val loadedScene = Scene("Loaded Scene", sceneInitializer)
        runBlocking {
            loadedScene.init()
        }
        sceneSerializer.open(loadedScene)
        if (loadedScene.sceneData.levelPath.isNotBlank()) {
            sceneManager.openSceneBlocking(loadedScene)
            openedScene = loadedScene
        } else {
            loadedScene.destroyScene()
            openedScene = null
        }
    }

    override fun undo() {
        // Open operations are not reversible — scene stack restoration is complex
    }

    override fun getDisplayName(): String = "Open Scene"
    override fun getTargetName(): String? = openedScene?.name
}
