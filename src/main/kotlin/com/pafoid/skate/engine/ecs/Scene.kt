package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.render.Camera
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

@Serializable
class Scene(@SerialName("sceneName") override var name: String = "MainScene") : GameObject(name) {

    // Camera remains a special property (not a component for now)
    val camera: Camera = Camera().also { it.position.set(Vector3f(0f, 5f, 20f)) }

    val gameObjects = mutableListOf<GameObject>()

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

    fun destroyScene() {
        super.destroy()
    }
}
