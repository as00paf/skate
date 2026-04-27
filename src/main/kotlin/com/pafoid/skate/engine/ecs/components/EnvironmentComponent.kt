package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Component containing environment rendering settings.
 *
 * This component stores all sky and fog configuration for environment rendering.
 * It should be added to the Scene GameObject to control global environment settings.
 *
 * @property skyColor Clear sky color (used for clear color and sky dome)
 * @property skyTint Sky color tint multiplier
 * @property skyExposure Sky exposure/brightness
 * @property skyRotation Sky rotation in degrees
 * @property fogColor Fog color
 * @property fogDensity Fog density (0 = no fog)
 * @property fogGradient Fog gradient falloff
 * @property renderSky Toggle sky rendering independently
 * @property renderFog Toggle fog rendering independently
 */
@Serializable
class EnvironmentComponent(
    @Contextual
    var skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f),

    @Contextual
    var skyTint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),

    var skyExposure: Float = 1.0f,

    var skyRotation: Float = 0.0f,

    @Contextual
    var fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),

    var fogDensity: Float = 0.0f,

    var fogGradient: Float = 1.5f,

    var renderSky: Boolean = true,

    var renderFog: Boolean = true
) : Component() {

    /**
     * Resets all properties to default values.
     * Restores typical clear daytime sky and no fog settings.
     */
    fun reset() {
        skyColor.set(0.6f, 0.7f, 0.9f)
        skyTint.set(1.0f, 1.0f, 1.0f)
        skyExposure = 1.0f
        skyRotation = 0.0f
        fogColor.set(0.8f, 0.8f, 0.8f)
        fogDensity = 0.0f
        fogGradient = 1.5f
        renderSky = true
        renderFog = true
    }

    /**
     * Applies an environment preset.
     *
     * @param preset The preset to apply
     */
    fun applyPreset(preset: EnvironmentPreset) {
        when (preset) {
            EnvironmentPreset.CLEAR_DAY -> {
                skyColor.set(0.6f, 0.7f, 0.9f)
                fogColor.set(0.6f, 0.7f, 0.9f)
                fogDensity = 0.0008f
                fogGradient = 0.8f
            }

            EnvironmentPreset.CLOUDY -> {
                skyColor.set(0.5f, 0.5f, 0.5f)
                fogColor.set(0.7f, 0.7f, 0.7f)
                fogDensity = 0.02f
                fogGradient = 1.0f
            }

            EnvironmentPreset.FOGGY -> {
                skyColor.set(0.7f, 0.7f, 0.7f)
                fogColor.set(0.8f, 0.8f, 0.8f)
                fogDensity = 0.05f
                fogGradient = 0.5f
            }

            EnvironmentPreset.SUNSET -> {
                skyColor.set(0.9f, 0.5f, 0.3f)
                fogColor.set(0.8f, 0.6f, 0.5f)
                fogDensity = 0.001f
                fogGradient = 1.2f
            }

            EnvironmentPreset.NO_FOG -> {
                skyColor.set(0.6f, 0.7f, 0.9f)
                fogColor.set(0.8f, 0.8f, 0.8f)
                fogDensity = 0.0f
                fogGradient = 1.5f
            }
        }
    }
}

