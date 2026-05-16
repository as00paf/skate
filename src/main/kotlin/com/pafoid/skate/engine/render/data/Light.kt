package com.pafoid.skate.engine.render.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class Light(
    @Contextual val position: Vector3f,
    @Contextual val color: Vector3f = Vector3f(1f, 1f, 1f)
)