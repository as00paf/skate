package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class ScenePhysicsComponent(
// TODO: not in use right now
    var debugEnabled: Boolean = false,
    @Contextual var gravity: Vector3f = Vector3f(0f, -9.81f, 0f),

    ) : Component() {
}