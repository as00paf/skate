package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Component containing lighting state.
 *
 * This component stores ambient light settings and state.
 * It should be added to the Scene GameObject to control global lighting state.
 *
 * @property ambientLight Ambient light color
 * @property useAmbient Whether to use ambient lighting
 */
@Serializable
data class LightingStateComponent(
    @Contextual
    var ambientLight: Vector3f = Vector3f(0.3f, 0.3f, 0.35f),
    var useAmbient: Boolean = true
) : Component() {

    override fun reset() {
        ambientLight.set(0.3f, 0.3f, 0.35f)
        useAmbient = true
    }
}
