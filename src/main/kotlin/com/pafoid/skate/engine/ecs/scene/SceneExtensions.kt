package com.pafoid.skate.engine.ecs.scene

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene

/**
 * Extension methods for Scene to provide convenient access to GameObjectManager functionality.
 */

/**
 * Sets the currently selected GameObject for editor purposes.
 */
fun Scene.setSelectedGameObject(gameObject: GameObject?) {
    this.gameObjectManager.setSelectedGameObject(gameObject)
}

/**
 * Gets the currently selected GameObject.
 */
fun Scene.getSelectedGameObject(): GameObject? = this.gameObjectManager.getSelectedGameObject()


fun Scene.createGameObject(name: String): GameObject {
    return gameObjectManager.createGameObject(name)
}

fun Scene.addGameObjectToScene(gameObject: GameObject) {
    gameObjectManager.addGameObject(gameObject, this.isRunning)
}

fun Scene.removeGameObject(gameObject: GameObject) {
    gameObjectManager.removeGameObject(gameObject)
}

fun Scene.getGameObject(id: Int): GameObject? {
    return gameObjectManager.getGameObject(id)
}

fun Scene.getGameObject(name: String): GameObject? {
    return gameObjectManager.getGameObject(name)
}