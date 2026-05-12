package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.ecs.Scene

/**
 * Command for saving a scene to a new file path (Save As).
 * This is execute-only as save operations are not reversible.
 */
class SaveSceneAsCommand(
    private val scene: Scene,
    private val sceneSerializer: SceneSerializer
) : Command {
    override fun execute() {
        sceneSerializer.saveAs(scene)
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Scene As"
    override fun getTargetName(): String? = scene.name
}
