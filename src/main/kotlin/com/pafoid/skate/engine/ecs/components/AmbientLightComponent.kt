package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class AmbientLightComponent(
    @Contextual
    val lightColor: Vector3f = Vector3f(0.3f, 0.3f, 0.35f),
    var intensity: Float = 1f,
) : Component() {

    override fun reset() {
        lightColor.set(0.3f, 0.3f, 0.35f)
        intensity = 1f
    }
}
