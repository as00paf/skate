package com.pafoid.skate.engine.ecs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Scene(@SerialName("sceneName") override var name: String = "MainScene") : GameObject(name) {

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
