package com.pafoid.skate.engine.ecs.scene

import com.pafoid.skate.engine.render.data.DirectionalLight
import com.pafoid.skate.engine.render.data.Light
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Scene-wide data and settings.
 *
 * This class holds serializable scene configuration that persists across saves.
 *
 * Note: Sky and fog environment settings have been moved to [EnvironmentSystem.config].
 * This class now only contains core scene data that is not managed by ECS systems.
 *
 * @property light Ambient point light at camera position (legacy)
 * @property sun Directional light configuration
 * @property useAmbient Whether to use ambient lighting
 * @property timeOfDay Time of day in hours (0-24)
 * @property ambientLight Manual ambient light color (when auto-ambient is disabled)
 * @property timeScale Time scaling factor (1.0 = normal speed)
 * @property gravity Gravity vector for physics simulation
 * @property levelPath Path to the level save file
 */
@Serializable
data class SceneData(
    @Contextual var light: Light = Light(Vector3f(0f, 0f, 20f)),
    var sun: DirectionalLight = DirectionalLight(),
    var useAmbient: Boolean = true,
    var timeOfDay: Float = 12.0f,
    @Contextual var ambientLight: Vector3f = Vector3f(0.3f, 0.3f, 0.35f),
    var timeScale: Float = 1.0f,
    @Contextual var gravity: Vector3f = Vector3f(0.0f, 9.81f, 0.0f),
    var levelPath: String = "level.json",
)