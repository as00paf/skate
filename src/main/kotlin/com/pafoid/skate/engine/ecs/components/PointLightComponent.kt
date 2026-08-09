package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class PointLightComponent(
    @Contextual
    var direction: Vector3f = Vector3f(0f, -1f, 0f),
    @Contextual
    var color: Vector3f = Vector3f(1f, 0.95f, 0.8f),
    var intensity: Float = 1f,
    var cutOff: Float = 1f,
) : Component() {

    override fun reset() {
        direction.set(0f, -1f, 0f)
        color.set(1f, 0.95f, 0.8f)
        intensity = 1f
        cutOff = 1f
    }
}
