package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.scene.SceneData
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.physics3d.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.render.Camera

/**
 * Scene represents the root of the ECS hierarchy and manages all game objects and systems.
 *
 * Scene extends GameObject to support component-based architecture for global scene state.
 * Components like EnvironmentComponent, TimeComponent, and LightingComponent can be added
 * to the Scene to store global state.
 *
 * Note: Scene itself is not serialized. Only its components and child GameObjects are serialized.
 *
 * @param name Scene name
 * @param initializer Scene initializer for loading scene-specific content
 */
open class Scene(
    name: String = "Scene",
    val initializer: SceneInitializer
) : GameObject(name) {

    // SceneData for minimal serializable configuration (levelPath, etc.)
    var sceneData: SceneData = SceneData()

    // Camera remains a special property (not a component for now)
    val camera: Camera = Camera()

    // Scene-level managers (not components, these are infrastructure)
    val physics3d: IPhysics3D = BulletPhysics3D()
    val gameObjectManager: GameObjectManager = GameObjectManager(physics3d)
    //val systemManager: SystemManager = SystemManager()

    var isRunning: Boolean = false
    var isDirty: Boolean = false

    suspend fun init() {
        initializer.loadResources(this)
        initializer.init(this)
    }

    fun startScene() {
        isRunning = true

        // Start this GameObject (Scene) components first
        super.start()

        // Then start all child game objects
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

    fun editorUpdateScene(dt: Float) {
        val timeScale = getComponent<TimeComponent>()?.timeScale ?: 1.0f
        val scaledDt = dt * timeScale
        camera.update(dt)
        physics3d.update(scaledDt)
        gameObjectManager.editorUpdate(dt)

        // Update Scene components
        super.editorUpdate(scaledDt)
    }

    fun updateScene(dt: Float) {
        val timeScale = getComponent<TimeComponent>()?.timeScale ?: 1.0f
        val scaledDt = dt * timeScale
        camera.update(scaledDt)
        physics3d.update(scaledDt)
        gameObjectManager.update(scaledDt)

        // Update Scene components
        super.update(scaledDt)
    }

    fun destroyScene() {
        gameObjectManager.destroy()
        super.destroy()
    }
}
