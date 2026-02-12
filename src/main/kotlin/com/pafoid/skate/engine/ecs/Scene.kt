package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.scene.SceneData
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.Physics3D
import com.pafoid.skate.engine.render.Camera

class Scene(
    val name: String = "Scene",
    val initializer: SceneInitializer,
    val camera: Camera = Camera()
) {

    var sceneData: SceneData = SceneData()
    val physics3d: IPhysics3D = Physics3D()
    val gameObjectManager: GameObjectManager = GameObjectManager(physics3d)
    val systemManager: SystemManager = SystemManager()

    var isRunning: Boolean = false

    /**
     * Adds a Component as a system to the scene. If the scene is running, adds it to pending systems
     * to be processed in the next update cycle.
     */
    fun addSystem(system: Component) {
        systemManager.addSystem(system, isRunning)
    }

    /**
     * Removes a Component from the scene.
     */
    fun removeSystem(system: Component) {
        systemManager.removeSystem(system)
    }

    suspend fun init() {
        initializer.loadResources(this)
        initializer.init(this)
    }

    fun start() {
        isRunning = true
        gameObjectManager.gameObjects.forEach { go ->
            go.start()
            physics3d.add(go)
        }

        // Flush any objects added during startup
        while (gameObjectManager.pendingObjects.isNotEmpty()) {
            val toAdd = mutableListOf<GameObject>()
            toAdd.addAll(gameObjectManager.pendingObjects)
            gameObjectManager.pendingObjects.clear()

            toAdd.forEach { go ->
                gameObjectManager.gameObjects.add(go)
                go.start()
                physics3d.add(go)
            }
        }
    }

    fun editorUpdate(dt: Float) {
        camera.update(dt)
        gameObjectManager.editorUpdate(dt)
        systemManager.editorUpdate(dt)
    }

    fun update(dt: Float) {
        val scaledDt = dt * sceneData.timeScale
        camera.update(scaledDt)
        physics3d.update(scaledDt)
        gameObjectManager.update(scaledDt)
        systemManager.update(dt)
    }

    fun imgui() {
        initializer.imgui()
    }

    fun destroy() {
        gameObjectManager.destroy()
        systemManager.destroy()
    }

}