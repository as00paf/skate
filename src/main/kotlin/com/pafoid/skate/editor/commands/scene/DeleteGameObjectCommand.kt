package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager

class DeleteGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
    private val gameObjectManager: GameObjectManager,
) : Command {

    override fun execute() {
        gameObjectManager.removeGameObject(gameObject)
        scene.selectedGameObject = null
    }

    override fun undo() {
        gameObjectManager.addGameObject(gameObject)
        scene.selectedGameObject = gameObject
    }

    override fun getDisplayName(): String = "Delete GameObject"
    override fun getTargetName(): String? = gameObject.name
}