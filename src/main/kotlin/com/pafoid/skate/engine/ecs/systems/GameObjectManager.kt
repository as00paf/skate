package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority

class GameObjectManager : System(priority = ExecutionPriority.EARLY) {

    override fun init(scene: Scene) {
        super.init(scene)
        scene.children.forEach {
            it.components.forEach { component -> component.init(it) }
            it.start()
        }
    }

    override fun start() {
        scene.children.forEach { go ->
            go.start()
        }
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        val iterator = scene.children.iterator()
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
        scene.children.add(gameObject)
        gameObject.start()
    }

    fun removeGameObject(gameObject: GameObject) {
        scene.children.remove(gameObject)
        gameObject.destroy()
    }

    fun getGameObject(id: Int): GameObject? {
        return scene.children.firstOrNull { it.uId == id }
    }

    fun getGameObject(name: String): GameObject? {
        return scene.children.firstOrNull { it.name == name }
    }

    fun reset() {
        scene.reset()
    }
    
    override fun destroy() {
        scene.children.forEach { it.destroy() }
        scene.children.clear()
    }
}
