package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.game.level.LevelManager

class OpenSceneCommand(
    private val scene: Scene,
    private val levelManager: LevelManager,
    private val filePath: String
) : Command {
    override fun execute() {
        levelManager.loadFromFile(scene, filePath)
    }

    override fun undo() {
        // Open operations are not reversible — scene would need to be re-closed
    }

    override fun getDisplayName(): String = "Open Scene"
    override fun getTargetName(): String = filePath
}
