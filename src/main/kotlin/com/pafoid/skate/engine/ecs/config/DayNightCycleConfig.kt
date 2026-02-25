package com.pafoid.skate.engine.ecs.config

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Configuration and state for the day/night cycle system.
 *
 * This data class is owned by [DayNightCycleSystem] and stores both
 * configuration parameters and computed state values.
 *
 * ## Configuration Properties
 *
 * - [cycleTime]: Current time in hours (0-24)
 * - [dayDuration]: Duration of one full cycle in seconds
 *
 * ## Computed Properties (updated by DayNightCycleSystem)
 *
 * - [sunDirection]: Current sun direction vector
 * - [sunColor]: Interpolated sun color through day phases
 * - [ambientColor]: Interpolated ambient light color
 * - [sunIntensity]: Sun intensity (0 at night, 1 at noon)
 * - [shadowIntensity]: Shadow intensity (lower at night)
 * - [isDaytime]: True when sun is above horizon
 */
@Serializable
data class DayNightCycleConfig(
    // =========================================================================
    // CYCLE CONFIGURATION
    // =========================================================================

    /**
     * Current time in the day/night cycle.
     * Range: 0.0 - 24.0 hours (0 = midnight, 6 = dawn, 12 = noon, 18 = dusk)
     * Default: 12f (noon)
     */
    var cycleTime: Float = 12f,

    /**
     * Duration of one full day/night cycle in real-time seconds.
     * Default: 300 seconds (5 minutes per day)
     */
    var dayDuration: Float = 300f,

    // =========================================================================
    // COMPUTED VALUES (updated by DayNightCycleSystem each frame)
    // =========================================================================

    /**
     * Computed sun direction vector based on cycleTime.
     * Updated each frame using trigonometric calculation.
     * - Noon: (0, 1, 0) - sun at zenith
     * - Dawn: (-1, 0, 0) - sun rises in east
     * - Dusk: (1, 0, 0) - sun sets in west
     * - Night: (0, -1, 0) - sun below
     */
    @Contextual
    var sunDirection: Vector3f = Vector3f(0f, -1f, 0f),

    /**
     * Computed sun color interpolated through day phases.
     * - Noon: warm yellow (1.0, 0.95, 0.8)
     * - Dusk: orange (1.0, 0.6, 0.3)
     * - Night: cool blue (0.3, 0.4, 0.6)
     * - Dawn: pink/orange (1.0, 0.7, 0.5)
     */
    @Contextual
    var sunColor: Vector3f = Vector3f(1f, 1f, 1f),

    /**
     * Computed ambient light color interpolated with sky color.
     * Darker at night, brighter during day.
     */
    @Contextual
    var ambientColor: Vector3f = Vector3f(0.5f, 0.5f, 0.5f),

    /**
     * Computed sun intensity based on sun height.
     * Full intensity at noon, zero at night.
     * Range: 0.0 - 1.0
     */
    var sunIntensity: Float = 1f,

    /**
     * Computed shadow intensity (lower at night when shadows are less visible).
     * Range: 0.0 - 1.0
     */
    var shadowIntensity: Float = 1f,

    /**
     * True if the sun is above the horizon (cycleTime between 6 and 18).
     * Updated each frame.
     */
    var isDaytime: Boolean = true,

    // =========================================================================
    // AMBIENT LIGHT CONFIGURATION
    // =========================================================================

    /**
     * Base ambient light intensity multiplier.
     * Controls overall brightness of ambient lighting.
     * Range: 0.0 - 2.0 (default: 1.0)
     */
    var ambientIntensity: Float = 1.0f,

    /**
     * When true, ambient light is automatically computed from day/night cycle.
     * When false, ambient light is controlled manually via Environment Window.
     * Default: true (automatic)
     */
    var autoAmbient: Boolean = true
) {
    /**
     * Resets all computed values to defaults.
     * Configuration values (cycleTime, dayDuration) are preserved.
     * Called by DayNightCycleSystem when cycle needs to be reset.
     */
    fun resetComputedValues() {
        sunDirection.set(0f, -1f, 0f)
        sunColor.set(1f, 1f, 1f)
        ambientColor.set(0.5f, 0.5f, 0.5f)
        sunIntensity = 1f
        shadowIntensity = 1f
        isDaytime = true
        ambientIntensity = 1.0f
    }

    /**
     * Resets all properties to defaults.
     */
    fun reset() {
        cycleTime = 12f
        dayDuration = 300f
        resetComputedValues()
    }
}
