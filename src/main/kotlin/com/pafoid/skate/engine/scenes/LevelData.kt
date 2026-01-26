package com.pafoid.skate.engine.scenes

import org.joml.Vector3f

data class LevelData(
    val gameObjects: List<GameObject>,
    val ambientLight: Vector3f,
    val lightPosition: Vector3f,
    val gravity: Vector3f,
    val fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),
    val fogDensity: Float = 0.0f,
    val fogGradient: Float = 1.5f
)
