package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.ecs.scene.SceneData
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.physics3d.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.render.Camera

class Scene(
    val name: String = "Scene",
    val initializer: SceneInitializer,
    val camera: Camera = Camera()
) {

    var sceneData: SceneData = SceneData()
    val physics3d: IPhysics3D = BulletPhysics3D()
    val gameObjectManager: GameObjectManager = GameObjectManager(physics3d)
    val systemManager: SystemManager = SystemManager()

    var isRunning: Boolean = false

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

        // Initialize and start systems
        systemManager.systems.forEach { system ->
            system.init(this)
            system.start()
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