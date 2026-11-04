package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.EcsChanges
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getAllChildren

class GameObjectManager : System(priority = ExecutionPriority.EARLY) {

    override fun init(scene: Scene) {
        super.init(scene)
        scene.getAllChildren().forEach {
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
                EcsChanges.touch()
                continue
            }
            go.update(dt)
        }
    }

    fun addGameObject(gameObject: GameObject) {
        scene.assertSceneMutationOnMainThread("addGameObject")
        scene.children.add(gameObject)
        EcsChanges.touch()
        gameObject.start()
    }

    fun removeGameObject(gameObject: GameObject) {
        scene.assertSceneMutationOnMainThread("removeGameObject")
        scene.children.remove(gameObject)
        EcsChanges.touch()
        gameObject.destroy()
    }

    private val idIndex = HashMap<Int, GameObject>()
    private var indexVersion = -1L
    private var indexRoot: Scene? = null

    /** Times the id index was rebuilt; exposed for tests. */
    var indexRebuildCount = 0
        private set

    /**
     * Object with [id] anywhere in the scene hierarchy. Backed by an id index rebuilt lazily when the
     * scene or the ECS structure version ([EcsChanges]) changes, so add/remove/reparent stay consistent.
     * If two objects share an id, the first in pre-order wins.
     */
    fun getGameObject(id: Int): GameObject? {
        val currentRoot = scene
        if (indexVersion != EcsChanges.version || indexRoot !== currentRoot) {
            idIndex.clear()
            fun walk(node: GameObject) {
                for (child in node.children) {
                    idIndex.putIfAbsent(child.uId, child)
                    walk(child)
                }
            }
            walk(currentRoot)
            indexVersion = EcsChanges.version
            indexRoot = currentRoot
            indexRebuildCount++
        }
        return idIndex[id]
    }

    /** First object named [name] anywhere in the scene hierarchy, pre-order. */
    fun getGameObject(name: String): GameObject? {
        fun walk(node: GameObject): GameObject? {
            for (child in node.children) {
                if (child.name == name) return child
                walk(child)?.let { return it }
            }
            return null
        }
        return walk(scene)
    }

    fun reset() {
        scene.reset()
    }
    
    override fun destroy() {
        scene.children.forEach { it.destroy() }
        scene.children.clear()
        EcsChanges.touch()
    }
}
