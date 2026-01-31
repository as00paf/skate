package com.pafoid.skate.engine.scenes

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class LevelData(
    val gameObjects: List<GameObject>,
    @Contextual val ambientLight: Vector3f,
    val useAmbient: Boolean = true,
    val useSun: Boolean = true,
    val timeOfDay: Float = 12.0f,
    @Contextual val skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f),
    @Contextual val skyTint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),
    val skyExposure: Float = 1.0f,
    val skyRotation: Float = 0.0f,
    @Contextual val sunDirection: Vector3f = Vector3f(-1f, -1f, -1f).normalize(),
    @Contextual val sunColor: Vector3f = Vector3f(1f, 1f, 1f),
    @Contextual val moonDirection: Vector3f = Vector3f(1f, 1f, 1f).normalize(),
    @Contextual val moonColor: Vector3f = Vector3f(0.4f, 0.4f, 0.6f),
    @Contextual val lightPosition: Vector3f,
    @Contextual val gravity: Vector3f,
    @Contextual val fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),
    val fogDensity: Float = 0.0f,
    val fogGradient: Float = 1.5f
)
