package com.pafoid.skate.engine.render

import org.joml.Vector3f

data class Light (val position: Vector3f, val color: Vector3f = Vector3f(1f, 1f, 1f))

data class DirectionalLight(
    val direction: Vector3f = Vector3f(-1f, -1f, -1f).normalize(),
    val color: Vector3f = Vector3f(1f, 1f, 1f),
    var intensity: Float = 1.0f
)