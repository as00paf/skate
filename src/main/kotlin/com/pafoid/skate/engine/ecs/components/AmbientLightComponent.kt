package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class AmbientLightComponent(
    @Contextual
    val lightColor: Vector3f = Vector3f(1f),
    var intensity: Float = .25f,
) : Component() {

    override fun reset() {
        lightColor.set(1f)
        intensity = .25f
    }
}
