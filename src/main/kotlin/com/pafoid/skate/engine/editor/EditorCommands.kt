package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.Transform

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
    private val sceneManager: SceneManager
) : Command {
    override fun execute() {
        scene.addGameObjectToScene(gameObject)
        sceneManager.setSelectedGameObject(gameObject)
    }

    override fun undo() {
        scene.removeGameObject(gameObject)
        sceneManager.setSelectedGameObject(null)
    }
}

class DeleteGameObjectCommand(
    private val gameObject: GameObject,
    private val scene: Scene,
    private val sceneManager: SceneManager
) : Command {
    override fun execute() {
        scene.removeGameObject(gameObject)
        sceneManager.setSelectedGameObject(null)
    }

    override fun undo() {
        scene.addGameObjectToScene(gameObject)
        sceneManager.setSelectedGameObject(gameObject)
    }
}
