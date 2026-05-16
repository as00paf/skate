package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Component containing computed lighting state from day/night cycle.
 *
 * This component stores the computed lighting values that are updated by DayNightCycleSystem.
 * It should be added to the Scene GameObject to store dynamic lighting state.
 *
 * @property sunDirection Current sun direction vector
 * @property sunColor Current sun color (interpolated through day phases)
 * @property sunIntensity Sun intensity (0 at night, 1 at noon)
 * @property shadowIntensity Shadow intensity (lower at night)
 * @property isDaytime True if sun is above horizon
 */
@Serializable
class LightingComponent(
    @Contextual
    var sunDirection: Vector3f = Vector3f(0f, -1f, 0f),

    @Contextual
    var sunColor: Vector3f = Vector3f(1f, 1f, 1f),

    var sunIntensity: Float = 1f,

    var shadowIntensity: Float = 1f,

    var isDaytime: Boolean = true
) : Component() {

    /**
     * Resets all properties to default values.
     */
    fun reset() {
        sunDirection.set(0f, -1f, 0f)
        sunColor.set(1f, 1f, 1f)
        sunIntensity = 1f
        shadowIntensity = 1f
        isDaytime = true
    }
}
