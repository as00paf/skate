package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.scene.removeGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject

class DeleteGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
) : Command {
    override fun execute() {
        scene.removeGameObject(gameObject)
        scene.setSelectedGameObject(null)
    }

    override fun undo() {
        scene.addGameObjectToScene(gameObject)
        scene.setSelectedGameObject(gameObject)
    }

    override fun getDisplayName(): String = "Delete GameObject"
    override fun getTargetName(): String? = gameObject.name
}