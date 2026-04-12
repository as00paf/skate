package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.editor.project.SceneSerializer

class OpenSceneCommand(
    private val scene: Scene,
    private val sceneSerializer: SceneSerializer,
    private val filePath: String
) : Command {
    override fun execute() {
        sceneSerializer.loadFromFile(scene, filePath)
    }

    override fun undo() {
        // Open operations are not reversible — scene would need to be re-closed
    }

    override fun getDisplayName(): String = "Open Scene"
    override fun getTargetName(): String = filePath
}
