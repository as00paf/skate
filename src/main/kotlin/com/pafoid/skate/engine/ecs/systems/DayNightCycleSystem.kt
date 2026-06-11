package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.hasComponent
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * System responsible for updating the day/night cycle.
 *
 * This system runs at [ExecutionPriority.EARLY] to ensure day/night state
 * is ready before lighting and shadow systems read from [config].
 *
 * ## Responsibilities
 *
 * - Advances [DayNightCycleComponent.cycleTime] based on delta time
 * - Computes sun direction from cycle time using trigonometry
 * - Interpolates sun color through day phases (daylight → dusk → night → dawn)
 * - Computes ambient color and shadow intensity
 *
 * ## Day Phases
 *
 * | Time | Phase | Sun Color | Ambient |
 * |------|-------|-----------|---------|
 * | 0-5 | Night | Cool blue (0.3, 0.4, 0.6) | Dark (0.1) |
 * | 5-7 | Dawn | Pink/orange (1.0, 0.7, 0.5) | Rising |
 * | 7-17 | Day | Warm yellow (1.0, 0.95, 0.8) | Bright (0.8) |
 * | 17-19 | Dusk | Orange (1.0, 0.6, 0.3) | Falling |
 * | 19-24 | Night | Cool blue (0.3, 0.4, 0.6) | Dark (0.1) |
 *
 * ## Sun Direction Calculation
 *
 * Sun position follows a simple arc:
 * - Noon (12:00): sun at zenith (0, 1, 0)
 * - Dawn (6:00): sun at horizon (-1, 0, 0)
 * - Dusk (18:00): sun at horizon (1, 0, 0)
 * - Midnight (0:00): sun below (0, -1, 0)
 *
 * @param dayDurationOverride Optional override for day duration in seconds
 * @param stringManager String manager for localized UI strings
 */
class DayNightCycleSystem(
    private val dayDurationOverride: Float? = null,
    private val stringManager: StringManager
) : System(priority = ExecutionPriority.EARLY) {

    // Color constants for interpolation
    private val noonColor = Vector3f(1.0f, 0.95f, 0.8f)  // Warm sunlight
    private val duskColor = Vector3f(1.0f, 0.6f, 0.3f)   // Orange sunset
    private val nightColor = Vector3f(0.3f, 0.4f, 0.6f)  // Cool moonlight
    private val dawnColor = Vector3f(1.0f, 0.7f, 0.5f)   // Pink/orange dawn
    private val nightAmbient = Vector3f(0.05f, 0.05f, 0.1f)
    private val dayAmbient = Vector3f(0.3f, 0.3f, 0.35f)

    override fun update(dt: Float) {
        val config = scene.getComponent<DayNightCycleComponent>() ?: return

        // Get day duration (use override if provided)
        val dayDuration = dayDurationOverride ?: config.dayDuration

        // Advance cycle time (convert dt to hours)
        val hoursPerSecond = 24f / dayDuration
        config.cycleTime = (config.cycleTime + dt * hoursPerSecond) % 24f

        // Compute sun direction from cycle time
        updateSunDirection(config)

        // Interpolate sun color based on time of day
        updateSunColor(config)

        // Update derived values
        config.isDaytime = config.cycleTime in 6f..18f
        config.shadowIntensity = if (config.isDaytime) 1f else 0.3f

        // Update scene ambient light if auto mode is enabled
        if (config.autoAmbient) {
            updateSceneAmbient(config)
        }
    }

    /**
     * Updates LightingStateComponent with computed ambient color and intensity.
     * Only called when autoAmbient is enabled.
     */
    private fun updateSceneAmbient(config: DayNightCycleComponent) {
        val lightingStateComponent = scene.getComponent<LightingStateComponent>()
            ?: LightingStateComponent()
        lightingStateComponent.ambientLight.set(config.ambientColor).mul(config.ambientIntensity)
        if (!scene.hasComponent<LightingStateComponent>()) {
            scene.addComponent(lightingStateComponent)
        }
    }

    /**
     * Computes sun direction vector from cycle time.
     *
     * Sun follows a simple arc in the X-Y plane:
     * - Angle 0° at noon (sun at zenith)
     * - Angle -90° at dawn (sun rises in east)
     * - Angle +90° at dusk (sun sets in west)
     * - Angle ±180° at midnight (sun below)
     */
    private fun updateSunDirection(config: DayNightCycleComponent) {
        // Convert cycle time to angle (0-24 hours → 0-360 degrees)
        // Offset by 6 hours so noon = 0° (sun at zenith)
        val hoursFromNoon = config.cycleTime - 12f
        val angleRadians = Math.toRadians(((hoursFromNoon / 24f) * 360f - 90f).toDouble())

        // Sun direction: Y component is sin (height), X component is cos (horizontal)
        config.sunDirection.set(
            cos(angleRadians).toFloat(),
            sin(angleRadians).toFloat(),
            0f
        ).normalize()
    }

    /**
     * Interpolates sun color based on time of day.
     *
     * Blends between dawn, noon, dusk, and night colors
     * to create smooth transitions through day phases.
     */
    private fun updateSunColor(config: DayNightCycleComponent) {
        val time = config.cycleTime

        // Determine current phase and interpolation factor
        when {
            // Dawn: 5-7 hours (blend night → dawn → noon)
            time < 7f -> {
                val t = ((time - 5f) / 2f).coerceIn(0f, 1f)
                if (time < 6f) {
                    // Night to dawn
                    lerpColor(nightColor, dawnColor, t, config.sunColor)
                } else {
                    // Dawn to noon
                    lerpColor(dawnColor, noonColor, t, config.sunColor)
                }
                config.sunIntensity = t
            }

            // Day: 7-17 hours (full brightness)
            time < 17f -> {
                config.sunColor.set(noonColor)
                config.sunIntensity = 1f
            }

            // Dusk: 17-19 hours (blend noon → dusk → night)
            time < 19f -> {
                val t = ((time - 17f) / 2f).coerceIn(0f, 1f)
                if (time < 18f) {
                    // Noon to dusk
                    lerpColor(noonColor, duskColor, t, config.sunColor)
                } else {
                    // Dusk to night
                    lerpColor(duskColor, nightColor, t, config.sunColor)
                }
                config.sunIntensity = 1f - t
            }

            // Night: 19-5 hours (dark)
            else -> {
                config.sunColor.set(nightColor)
                config.sunIntensity = 0f
            }
        }

        // Compute ambient color (interpolates between night and day ambient)
        // Intensity multiplier is applied in updateSceneAmbient() so slider updates work
        config.ambientColor.set(nightAmbient).lerp(dayAmbient, config.sunIntensity)
    }

    /**
     * Linear interpolation between two colors.
     */
    private fun lerpColor(from: Vector3f, to: Vector3f, t: Float, result: Vector3f) {
        result.set(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.z + (to.z - from.z) * t
        )
    }
}
