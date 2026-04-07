package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.scene.removeGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject

class CreateGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
) : Command {
    override fun execute() {
        scene.addGameObjectToScene(gameObject)
        scene.setSelectedGameObject(gameObject)
    }

    override fun undo() {
        scene.removeGameObject(gameObject)
        scene.setSelectedGameObject(null)
    }

    override fun getDisplayName(): String = "Create GameObject"
    override fun getTargetName(): String? = gameObject.name
}