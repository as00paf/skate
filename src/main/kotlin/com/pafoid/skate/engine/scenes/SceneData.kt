package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.Physics3D
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.DirectionalLight
import com.pafoid.skate.engine.render.Light
import org.joml.Vector3f

data class SceneData(
    var light: Light = Light(Vector3f(0f, 0f, 20f)),
    var sun: DirectionalLight = DirectionalLight(),
    var moon: DirectionalLight = DirectionalLight(),
    var useSun: Boolean = true,
    var useAmbient: Boolean = true,
    var timeOfDay: Float = 12.0f, // 0.0 to 24.0, 12.0 is noon
    var ambientLight: Vector3f = Vector3f(0.3f, 0.3f, 0.35f), // Brighter ambient light
    var skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f),
    var skyTint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),
    var skyExposure: Float = 1.0f,
    var skyRotation: Float = 0.0f,
    var fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),
    var fogDensity: Float = 0.0f,
    var fogGradient: Float = 1.5f,
    var timeScale: Float = 1.0f,
    var levelPath: String = "level.json",
    val physics3d: IPhysics3D = Physics3D(),
    var isRunning: Boolean = false
)