package com.pafoid.skate.engine.ecs.scene

import com.pafoid.skate.engine.render.data.DirectionalLight
import com.pafoid.skate.engine.render.data.Light
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class SceneData(
    @Contextual var light: Light = Light(Vector3f(0f, 0f, 20f)),
    var sun: DirectionalLight = DirectionalLight(),
    var useAmbient: Boolean = true,
    var timeOfDay: Float = 12.0f,
    @Contextual var ambientLight: Vector3f = Vector3f(0.3f, 0.3f, 0.35f),
    @Contextual var skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f),
    @Contextual var skyTint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),
    var skyExposure: Float = 1.0f,
    var skyRotation: Float = 0.0f,
    @Contextual var fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),
    var fogDensity: Float = 0.0f,
    var fogGradient: Float = 1.5f,
    var timeScale: Float = 1.0f,
    @Contextual var gravity: Vector3f = Vector3f(0.0f, 9.81f, 0.0f),
    var levelPath: String = "level.json",
)