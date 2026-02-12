package com.pafoid.skate.engine.render

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class DirectionalLight(
    @Contextual val direction: Vector3f = Vector3f(-1f, -1f, -1f).normalize(),
    @Contextual val color: Vector3f = Vector3f(1f, 1f, 1f),
    var intensity: Float = 1.0f
)