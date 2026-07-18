package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.config.ExecutionPriority

/**
 * Manages the lifecycle and operations of GameObjects within a scene.
 * This class centralizes all GameObject management responsibilities to reduce
 * the burden on the Scene class and improve separation of concerns.
 */
class GameObjectManager : System(priority = ExecutionPriority.EARLY) {

    override fun init(scene: Scene) {
        super.init(scene)
        scene.gameObjects.forEach {
            it.components.forEach { component -> component.init(it) }
            it.start()
        }
    }

    override fun start() {
        scene.gameObjects.forEach { go ->
            go.start()
        }
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return // TODO: check if still needed

        val iterator = scene.gameObjects.iterator()
        while (iterator.hasNext()) {
            val go = iterator.next()
            if (go.isDead) {
                iterator.remove()
                continue
            }
            go.update(dt)
        }
    }

    fun addGameObject(gameObject: GameObject) {
        scene.gameObjects.add(gameObject)
        gameObject.start()
    }

    fun removeGameObject(gameObject: GameObject) {
        scene.gameObjects.remove(gameObject)
        gameObject.destroy()
    }

    fun getGameObject(id: Int): GameObject? {
        return scene.gameObjects.firstOrNull { it.uId == id }
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
        scene.markObjectSetChanged()
    }
}
