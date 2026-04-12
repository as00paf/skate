package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.game.level.LevelManager

class SaveSceneCommand(
    private val scene: Scene,
    private val levelManager: LevelManager
) : Command {
    override fun execute() {
        levelManager.save(scene)
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Scene"
    override fun getTargetName(): String? = scene.name
}
