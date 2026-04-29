package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform

/**
 * Manages the lifecycle and operations of GameObjects within a scene.
 * This class centralizes all GameObject management responsibilities to reduce
 * the burden on the Scene class and improve separation of concerns.
 */
class GameObjectManager : System(priority = ExecutionPriority.EARLY) {

    override fun init(scene: Scene) {
        super.init(scene)
        scene.gameObjects.forEach { it.start() }
    }

    override fun start() {
        scene.gameObjects.forEach { go ->
            go.start()
            scene.physics3d.add(go)
        }

        while (scene.pendingObjects.isNotEmpty()) {
            val toAdd = mutableListOf<GameObject>()
            toAdd.addAll(scene.pendingObjects)
            scene.pendingObjects.clear()

            toAdd.forEach { go ->
                scene.gameObjects.add(go)
                go.start()
                scene.physics3d.add(go)
            }
        }
    }

    override fun update(dt: Float) {
        val iterator = scene.gameObjects.iterator()
        while (iterator.hasNext()) {
            val go = iterator.next()
            if (go.isDead()) {
                scene.physics3d.remove(go)
                iterator.remove()
                continue
            }
            go.update(dt)
        }

        processPendingObjects()
    }

    private fun processPendingObjects() {
        scene.pendingObjects.forEach { gameObject ->
            scene.gameObjects.add(gameObject)
            gameObject.start()
            scene.physics3d.add(gameObject)
        }

        scene.pendingObjects.clear()
    }

    fun addGameObject(gameObject: GameObject, isRunning: Boolean = false) {
        if (!isRunning) {
            scene.gameObjects.add(gameObject)
        } else {
            scene.pendingObjects.add(gameObject)
        }
    }

    /**
     * Adds a GameObject directly to gameObjects with full initialization.
     * Use for editor-driven additions (spawning prefabs, default content).
     * If the scene is running, calls start() and registers with physics immediately.
     * If the scene is not running, start()/physics will happen in startScene().
     */
    fun addGameObjectImmediate(gameObject: GameObject, isRunning: Boolean = false) {
        scene.gameObjects.add(gameObject)
        if (isRunning) {
            gameObject.start()
            scene.physics3d.add(gameObject)
        }
    }

    fun removeGameObject(gameObject: GameObject) {
        scene.gameObjects.remove(gameObject)
        scene.pendingObjects.remove(gameObject)
        scene.physics3d.remove(gameObject)
    }

    fun getGameObject(id: Int): GameObject? {
        return scene.gameObjects.firstOrNull { it.getUid() == id }
    }

    fun getGameObject(name: String): GameObject? {
        return scene.gameObjects.firstOrNull { it.name == name }
    }

    fun createGameObject(name: String): GameObject {
        val go = GameObject(name)
        go.addComponent(Transform())
        return go
    }

    override fun destroy() {
        scene.gameObjects.forEach { it.destroy() }
        scene.gameObjects.clear()
        scene.pendingObjects.clear()
    }
}