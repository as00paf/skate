package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager

class CreateGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
    private val gameObjectManager: GameObjectManager,
) : Command {

    override fun execute() {
        gameObjectManager.addGameObject(gameObject)
        scene.selectedGameObject = gameObject
    }

    override fun undo() {
        gameObjectManager.removeGameObject(gameObject)
        scene.selectedGameObject = null
    }

    override fun getDisplayName(): String = "Create GameObject"
    override fun getTargetName(): String? = gameObject.name
}