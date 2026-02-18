package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.IPhysics3D

/**
 * Manages the lifecycle and operations of GameObjects within a scene.
 * This class centralizes all GameObject management responsibilities to reduce
 * the burden on the Scene class and improve separation of concerns.
 */
class GameObjectManager(
    private val physics3d: IPhysics3D
) {
    val gameObjects = mutableListOf<GameObject>()
    val pendingObjects = mutableListOf<GameObject>()

    private var selectedGameObject: GameObject? = null

    /**
     * Adds a GameObject to the scene. If the scene is running, adds it to pending objects
     * to be processed in the next update cycle.
     */
    fun addGameObject(gameObject: GameObject, isRunning: Boolean = false) {
        if (!isRunning) {
            gameObjects.add(gameObject)
        } else {
            pendingObjects.add(gameObject)
        }
    }

    /**
     * Removes a GameObject from the scene and the physics system.
     */
    fun removeGameObject(gameObject: GameObject) {
        gameObjects.remove(gameObject)
        pendingObjects.remove(gameObject)
        physics3d.remove(gameObject)
    }

    /**
     * Gets a GameObject by its unique ID.
     */
    fun getGameObject(id: Int): GameObject? {
        return gameObjects.firstOrNull { it.getUid() == id }
    }

    /**
     * Gets a GameObject by its name.
     */
    fun getGameObject(name: String): GameObject? {
        return gameObjects.firstOrNull { it.name == name }
    }

    /**
     * Updates all GameObjects in the scene during editor mode.
     */
    fun editorUpdate(dt: Float) {
        val iterator = gameObjects.iterator()
        while (iterator.hasNext()) {
            val go = iterator.next()
            if (go.isDead()) {
                physics3d.remove(go)
                iterator.remove()
                continue
            }
            go.editorUpdate(dt)
            physics3d.update(go)
        }

        processPendingObjects()
    }

    /**
     * Updates all GameObjects in the scene during runtime.
     */
    fun update(dt: Float) {
        val iterator = gameObjects.iterator()
        while (iterator.hasNext()) {
            val go = iterator.next()
            if (go.isDead()) {
                physics3d.remove(go)
                iterator.remove()
                continue
            }
            go.update(dt)
        }

        processPendingObjects()
    }

    /**
     * Processes any pending GameObjects that need to be added to the scene.
     */
    private fun processPendingObjects() {
        pendingObjects.forEach { gameObject ->
            gameObjects.add(gameObject)
            gameObject.start()
            physics3d.add(gameObject)
        }

        pendingObjects.clear()
    }

    /**
     * Creates a new GameObject with the given name and adds a default Transform component.
     */
    fun createGameObject(name: String): GameObject {
        val go = GameObject(name)
        go.addComponent(Transform())
        return go
    }

    /**
     * Sets the currently selected GameObject for editor purposes.
     */
    fun setSelectedGameObject(gameObject: GameObject?) {
        selectedGameObject = gameObject
    }

    /**
     * Gets the currently selected GameObject.
     */
    fun getSelectedGameObject(): GameObject? = selectedGameObject

    /**
     * Destroys all GameObjects managed by this manager.
     */
    fun destroy() {
        gameObjects.forEach { it.destroy() }
        gameObjects.clear()
        pendingObjects.clear()
    }
}