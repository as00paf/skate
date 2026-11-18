package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction
import java.io.File

/**
 * Creates the scene file `<sceneDir>/<name>.scene` and opens the new scene (via [SceneAction.Created]).
 *
 * Undo closes the scene, deletes the file it wrote and returns to the scene that was active before (EDT-99). Redo
 * creates it again.
 */
class CreateSceneCommand(
    private val name: String,
    private val sceneDir: String,
    private val eventSystem: EventSystem,
    private val sceneManager: SceneManager,
) : Command {

    private var createdScene: Scene? = null
    private var previousScene: Scene? = null

    val sceneFile: File get() = File(sceneDir, "$name.scene")

    override fun execute() {
        previousScene = sceneManager.currentScene
        val scene = sceneManager.createNewScene(name, sceneDir)
        createdScene = scene
        eventSystem.publish(SceneAction.Created(scene))
    }

    override fun undo() {
        val scene = createdScene ?: return
        sceneManager.closeScene(scene)
        sceneFile.delete()
        createdScene = null
        previousScene?.takeIf { it in sceneManager.openScenes }?.let { sceneManager.switchScene(it) }
    }

    override fun getDisplayName(): String = "Create Scene"
    override fun getTargetName(): String? = name
}
