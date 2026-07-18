package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.data.DirectionalLight
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

/**
 * Scene represents the root of the ECS hierarchy and manages all game objects and systems.
 *
 * Scene extends GameObject to support component-based architecture for global scene state.
 * Components like EnvironmentComponent, TimeComponent, and LightingComponent can be added
 * to the Scene to store global state.
 *
 * @param name Scene name
 */
@Serializable
class Scene(@SerialName("sceneName") override var name: String = "MainScene") : GameObject(name) {

    // TODO: this should be DirectionalLightComponent
    var sun: DirectionalLight = DirectionalLight()

    // Camera remains a special property (not a component for now)
    val camera: Camera = Camera().also { it.position.set(Vector3f(0f, 5f, 20f)) }

    val gameObjects = mutableListOf<GameObject>()
    var objectSetVersion: Long = 0
        private set

    @Transient
    var hoveredGameObject: GameObject? = null
    @Transient
    var selectedGameObject: GameObject? = null

    var isRunning: Boolean = false
    var isDirty: Boolean = false

    override fun start() {
        isRunning = true
        super.start()
    }

    override fun update(dt: Float) {
        val timeScale = getComponent<TimeComponent>()?.timeScale ?: 1.0f
        val scaledDt = dt * timeScale
        // TODO: move ?
        camera.update(scaledDt)

        super.update(scaledDt)
    }

    fun destroyScene() {
        super.destroy()
    }

    fun markObjectSetChanged() {
        objectSetVersion++
    }
}
