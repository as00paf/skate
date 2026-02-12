package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.scene.removeGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject

class TransformCommand(
    private val gameObject: GameObject,
    oldTransform: Transform,
    newTransform: Transform
) : Command {
    private val oldT = Transform().apply { copyFrom(oldTransform) }
    private val newT = Transform().apply { copyFrom(newTransform) }

    override fun execute() {
        gameObject.getComponent<Transform>()?.copyFrom(newT)
    }

    override fun undo() {
        gameObject.getComponent<Transform>()?.copyFrom(oldT)
    }
}

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
}

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
}
