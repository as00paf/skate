package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.editor.project.SceneSerializer

class SaveSceneCommand(
    private val scene: Scene,
    private val sceneSerializer: SceneSerializer
) : Command {
    override fun execute() {
        sceneSerializer.save(scene)
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Scene"
    override fun getTargetName(): String? = scene.name
}
