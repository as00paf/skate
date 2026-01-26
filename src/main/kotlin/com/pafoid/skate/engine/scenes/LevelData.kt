package com.pafoid.skate.engine.scenes

import org.joml.Vector3f

data class LevelData(
    val gameObjects: List<GameObject>,
    val ambientLight: Vector3f,
    val useAmbient: Boolean = true,
    val useSun: Boolean = true,
    val timeOfDay: Float = 12.0f,
    val skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f),
    val sunDirection: Vector3f = Vector3f(-1f, -1f, -1f).normalize(),
    val sunColor: Vector3f = Vector3f(1f, 1f, 1f),
    val moonDirection: Vector3f = Vector3f(1f, 1f, 1f).normalize(),
    val moonColor: Vector3f = Vector3f(0.4f, 0.4f, 0.6f),
    val lightPosition: Vector3f,
    val gravity: Vector3f,
    val fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),
    val fogDensity: Float = 0.0f,
    val fogGradient: Float = 1.5f
)
