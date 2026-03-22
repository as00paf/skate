package com.pafoid.skate.engine.ecs.config

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Configuration for the environment system.
 *
 * This data class is owned by [EnvironmentSystem] and stores all environment
 * rendering settings including sky color, fog parameters, and atmosphere settings.
 *
 * ## Sky Properties
 *
 * - [skyColor]: Clear sky color (used for clear color and sky dome)
 * - [skyTint]: Sky color tint multiplier
 * - [skyExposure]: Sky exposure/brightness
 * - [skyRotation]: Sky rotation in degrees
 * - [renderSky]: Toggle sky rendering independently
 *
 * ## Fog Properties
 *
 * - [fogColor]: Fog color
 * - [fogDensity]: Fog density (0 = no fog)
 * - [fogGradient]: Fog gradient falloff
 * - [renderFog]: Toggle fog rendering independently
 *
 * @see EnvironmentSystem
 */
@Serializable
data class EnvironmentConfig(
    // =========================================================================
    // SKY CONFIGURATION
    // =========================================================================

    /**
     * Clear sky color.
     * Used as the clear color for the framebuffer and sky dome rendering.
     * Default: light blue (0.6, 0.7, 0.9) - typical clear daytime sky.
     */
    @Contextual
    var skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f),

    /**
     * Sky color tint multiplier.
     * Allows fine-tuning of sky color without changing base skyColor.
     * Default: (1.0, 1.0, 1.0) - no tint.
     * Range: 0.0 - 2.0 per channel.
     */
    @Contextual
    var skyTint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),

    /**
     * Sky exposure/brightness.
     * Controls overall brightness of the sky dome.
     * Default: 1.0
     * Range: 0.0 (dark) to 10.0 (very bright for HDR).
     */
    var skyExposure: Float = 1.0f,

    /**
     * Sky rotation in degrees.
     * Rotates the sky dome around the Y axis.
     * Useful for aligning sky features with the scene.
     * Default: 0.0
     * Range: 0.0 - 360.0 degrees.
     */
    var skyRotation: Float = 0.0f,

    /**
     * Toggle sky rendering independently from fog.
     * When false, sky dome is not rendered and framebuffer clears to fallback color.
     * Default: true
     */
    var renderSky: Boolean = true,

    // =========================================================================
    // FOG CONFIGURATION
    // =========================================================================

    /**
     * Fog color.
     * Color that objects blend toward based on distance and fog density.
     * Typically matches or is similar to skyColor for natural appearance.
     * Default: light gray (0.8, 0.8, 0.8).
     */
    @Contextual
    var fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),

    /**
     * Fog density.
     * Controls how quickly objects fade into fog with distance.
     * Default: 0.0 (no fog).
     * Range: 0.0 (no fog) to 1.0 (very dense fog).
     * Typical values: 0.0001 - 0.01 for subtle atmospheric effects.
     */
    var fogDensity: Float = 0.0f,

    /**
     * Fog gradient falloff.
     * Controls the rate of fog density change with height.
     * Higher values = fog concentrated near ground.
     * Lower values = more uniform fog distribution.
     * Default: 1.5
     * Range: 0.1 - 10.0.
     */
    var fogGradient: Float = 1.5f,

    /**
     * Toggle fog rendering independently from sky.
     * When false, fog uniforms are set to zero (no fog effect).
     * Default: true
     */
    var renderFog: Boolean = true
) {
    /**
     * Resets all properties to default values.
     *
     * Called by EnvironmentSystem when user requests reset via ImGui.
     * Restores typical clear daytime sky and no fog settings.
     */
    fun reset() {
        skyColor.set(0.6f, 0.7f, 0.9f)
        skyTint.set(1.0f, 1.0f, 1.0f)
        skyExposure = 1.0f
        skyRotation = 0.0f
        renderSky = true
        fogColor.set(0.8f, 0.8f, 0.8f)
        fogDensity = 0.0f
        fogGradient = 1.5f
        renderFog = true
    }

    /**
     * Applies a preset configuration.
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

