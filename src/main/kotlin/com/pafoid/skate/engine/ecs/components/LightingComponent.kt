package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
class LightingComponent(
    @Contextual
    var sunDirection: Vector3f = Vector3f(0f, -1f, 0f),

    @Contextual
    var sunColor: Vector3f = Vector3f(1f, 1f, 1f),

    var sunIntensity: Float = 1f,

    var shadowIntensity: Float = 1f,
) : Component() {

    /**
     * Resets all properties to default values.
     */
    fun reset() {
        sunDirection.set(0f, -1f, 0f)
        sunColor.set(1f, 1f, 1f)
        sunIntensity = 1f
        shadowIntensity = 1f
    }
}
