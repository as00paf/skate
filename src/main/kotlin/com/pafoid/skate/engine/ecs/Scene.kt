package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.scene.SceneData
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import com.pafoid.skate.engine.physics3d.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.render.Camera
import org.joml.Vector3f

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
    val camera: Camera = Camera(Vector3f(0f, 5f, 20f))

    val physics3d: IPhysics3D = BulletPhysics3D()

    val gameObjects = mutableListOf<GameObject>()
    val pendingObjects = mutableListOf<GameObject>()
    var hoveredGameObject: GameObject? = null
    var selectedGameObject: GameObject? = null

    var isRunning: Boolean = false
    var isDirty: Boolean = false

    suspend fun init() {
        initializer.loadResources(this)
        initializer.init(this)
    }

    override fun start() {
        isRunning = true
        super.start()
    }

    override fun update(dt: Float) {
        val timeScale = getComponent<TimeComponent>()?.timeScale ?: 1.0f
        val scaledDt = dt * timeScale
        // TODO: move ?
        camera.update(scaledDt)

        // TODO: move ?
        if (isRunning) {
            physics3d.update(scaledDt)
        }

        super.update(scaledDt)
    }

    fun destroyScene() {
        super.destroy()
    }
}
