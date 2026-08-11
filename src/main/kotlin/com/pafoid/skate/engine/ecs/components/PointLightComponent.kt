package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class PointLightComponent(
    @Contextual
    val color: Vector3f = Vector3f(1f),
    var intensity: Float = 1f,
    var constant: Float = 1f,
    var linear: Float = 0.1f,
    var quadratic: Float = 0.032f,
) : Component() {

    override fun reset() {
        constant = 1f
        linear = 0.1f
        quadratic = 0.032f
    }
}
