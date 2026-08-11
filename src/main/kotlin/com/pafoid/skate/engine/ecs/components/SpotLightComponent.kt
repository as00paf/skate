package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class SpotLightComponent(
    @Contextual val direction: Vector3f = Vector3f(0f, -1f, 0f),
    @Contextual val color: Vector3f = Vector3f(1f),
    var intensity: Float = 1f,
    var cutOff: Float = 12.5f,      // Inner cutoff angle (degrees)
    var outerCutOff: Float = 17.5f, // Outer cutoff angle (degrees)
    var constant: Float = 1.0f,
    var linear: Float = 0.09f,
    var quadratic: Float = 0.032f,
) : Component() {

    override fun reset() {
        intensity = 1f
        cutOff = 12.5f
        outerCutOff = 17.5f
        constant = 1.0f
        linear = 0.09f
        quadratic = 0.032f
    }
}