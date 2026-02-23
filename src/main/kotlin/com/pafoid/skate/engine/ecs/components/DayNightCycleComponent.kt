package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Component that stores day/night cycle state.
 *
 * This component is updated by [DayNightCycleSystem] and read by:
 * - [DirectionalLightSystem] for sun direction, color, and intensity
 * - Sky/fog systems for ambient color interpolation
 * - Shadow systems for shadow intensity adjustment
 *
 * ## Usage
 *
 * ```kotlin
 * // In DirectionalLightSystem or similar
 * val dayNight = gameObject.getComponent<DayNightCycleComponent>() ?: return
 *
 * // Get sun direction for light view matrix
 * val sunDir = dayNight.sunDirection
 *
 * // Get interpolated sun color (warm daylight → cool moonlight)
 * val sunColor = dayNight.sunColor
 *
 * // Get shadow intensity (lower at night)
 * val shadowIntensity = dayNight.shadowIntensity
 * ```
 */
@Serializable
class DayNightCycleComponent : Component() {

    // =========================================================================
    // CYCLE CONFIGURATION
    // =========================================================================

    /**
     * Current time in the day/night cycle.
     * Range: 0.0 - 24.0 hours (0 = midnight, 6 = dawn, 12 = noon, 18 = dusk)
     */
    var cycleTime: Float = 12f

    /**
     * Duration of one full day/night cycle in real-time seconds.
     * Default: 300 seconds (5 minutes per day)
     */
    var dayDuration: Float = 300f

    // =========================================================================
    // COMPUTED VALUES (set by DayNightCycleSystem)
    // =========================================================================

    /**
     * Computed sun direction vector based on cycleTime.
     * Updated each frame by DayNightCycleSystem using trigonometric calculation.
     */
    @Contextual
    var sunDirection = Vector3f(0f, -1f, 0f)

    /**
     * Computed sun color interpolated through day phases.
     * - Noon: warm yellow (1.0, 0.95, 0.8)
     * - Dusk: orange (1.0, 0.6, 0.3)
     * - Night: cool blue (0.3, 0.4, 0.6)
     * - Dawn: pink/orange (1.0, 0.7, 0.5)
     */
    @Contextual
    var sunColor = Vector3f(1f, 1f, 1f)

    /**
     * Computed ambient light color interpolated with sky color.
     * Darker at night, brighter during day.
     */
    @Contextual
    var ambientColor = Vector3f(0.5f, 0.5f, 0.5f)

    /**
     * Computed sun intensity based on sun height.
     * Full intensity at noon, zero at night.
     * Range: 0.0 - 1.0
     */
    var sunIntensity: Float = 1f

    /**
     * Computed shadow intensity (lower at night when shadows are less visible).
     * Range: 0.0 - 1.0
     */
    var shadowIntensity: Float = 1f

    /**
     * True if the sun is above the horizon (cycleTime between 6 and 18).
     * Updated each frame by DayNightCycleSystem.
     */
    var isDaytime: Boolean = true

    /**
     * Resets all computed values to defaults.
     * Called by DayNightCycleSystem when cycle is reset.
     */
    fun reset() {
        cycleTime = 12f
        sunDirection.set(0f, -1f, 0f)
        sunColor.set(1f, 1f, 1f)
        ambientColor.set(0.5f, 0.5f, 0.5f)
        sunIntensity = 1f
        shadowIntensity = 1f
        isDaytime = true
    }

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        reset()
    }
}
