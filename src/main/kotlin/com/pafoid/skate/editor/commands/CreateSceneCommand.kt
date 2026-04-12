package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.engine.ecs.SceneManager
import kotlinx.coroutines.runBlocking

/**
 * Command for creating a new scene.
 * This is execute-only as undoing a create operation would require
 * tracking and removing the newly created scene.
 */
class CreateSceneCommand(
    private val name: String,
    private val sceneInitializer: LevelEditorSceneInitializer,
    private val sceneManager: SceneManager
) : Command {
    override fun execute() {
        runBlocking {
            sceneManager.createScene(name, sceneInitializer)
        }
    }

    override fun undo() {
        // Not supported - would require tracking and removing the new scene
    }

    override fun getDisplayName(): String = "Create Scene"
    override fun getTargetName(): String? = name
}
