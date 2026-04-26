package com.pafoid.skate.engine.ecs.scene

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.System

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

/**
 * Adds a GameObject immediately to the scene's gameObjects list.
 * Use for editor-driven additions. Bypasses the pending queue.
 */
fun Scene.addGameObjectImmediate(gameObject: GameObject) {
    gameObjectManager.addGameObjectImmediate(gameObject, this.isRunning)
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

/**
 * Adds a System to the scene. If the scene is running, adds it to pending systems
 * to be processed in the next update cycle.
 */
fun Scene.addSystem(system: System) {
    systemManager.addSystem(system, isRunning)
    system.init(this)
}

/**
 * Removes a System from the scene.
 */
fun Scene.removeSystem(system: System) {
    systemManager.removeSystem(system)
}
