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
 * @property light Ambient point light at camera position (legacy)
 * @property sun Directional light configuration
 * @property useAmbient Whether to use ambient lighting
 * @property timeOfDay Time of day in hours (0-24)
 * @property ambientLight Manual ambient light color (when auto-ambient is disabled)
 * @property timeScale Time scaling factor (1.0 = normal speed)
 * @property gravity Gravity vector for physics simulation
 * @property levelPath Path to the level save file
 *
 * @deprecated Sky and fog environment settings (skyColor, skyTint, skyExposure, skyRotation,
 * fogColor, fogDensity, fogGradient) have been moved to [EnvironmentSystem.config].
 * These properties are kept for backwards compatibility with saved levels but are no longer used.
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

    // =========================================================================
    // DEPRECATED: Environment settings moved to EnvironmentSystem.config
    // Kept for backwards compatibility with saved levels
    // =========================================================================

    /** @deprecated Use EnvironmentSystem.config.skyColor instead */
    @Deprecated("Use EnvironmentSystem.config.skyColor instead", ReplaceWith(""))
    @Contextual var skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f),

    /** @deprecated Use EnvironmentSystem.config.skyTint instead */
    @Deprecated("Use EnvironmentSystem.config.skyTint instead", ReplaceWith(""))
    @Contextual var skyTint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),

    /** @deprecated Use EnvironmentSystem.config.skyExposure instead */
    @Deprecated("Use EnvironmentSystem.config.skyExposure instead", ReplaceWith(""))
    var skyExposure: Float = 1.0f,

    /** @deprecated Use EnvironmentSystem.config.skyRotation instead */
    @Deprecated("Use EnvironmentSystem.config.skyRotation instead", ReplaceWith(""))
    var skyRotation: Float = 0.0f,

    /** @deprecated Use EnvironmentSystem.config.fogColor instead */
    @Deprecated("Use EnvironmentSystem.config.fogColor instead", ReplaceWith(""))
    @Contextual var fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),

    /** @deprecated Use EnvironmentSystem.config.fogDensity instead */
    @Deprecated("Use EnvironmentSystem.config.fogDensity instead", ReplaceWith(""))
    var fogDensity: Float = 0.0f,

    /** @deprecated Use EnvironmentSystem.config.fogGradient instead */
    @Deprecated("Use EnvironmentSystem.config.fogGradient instead", ReplaceWith(""))
    var fogGradient: Float = 1.5f
)