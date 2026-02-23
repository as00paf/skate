package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * System responsible for updating the day/night cycle.
 *
 * This system runs at [ExecutionPriority.EARLY] to ensure day/night state
 * is ready before lighting and shadow systems read from [DayNightCycleComponent].
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
 */
class DayNightCycleSystem(
    private val dayDurationOverride: Float? = null
) : System(priority = ExecutionPriority.EARLY) {

    // Color constants for interpolation
    private val noonColor = Vector3f(1.0f, 0.95f, 0.8f)  // Warm sunlight
    private val duskColor = Vector3f(1.0f, 0.6f, 0.3f)   // Orange sunset
    private val nightColor = Vector3f(0.3f, 0.4f, 0.6f)  // Cool moonlight
    private val dawnColor = Vector3f(1.0f, 0.7f, 0.5f)   // Pink/orange dawn
    private val nightAmbient = Vector3f(0.05f, 0.05f, 0.1f)
    private val dayAmbient = Vector3f(0.3f, 0.3f, 0.35f)

    // Temporary vectors for interpolation
    private val tempColor = Vector3f()

    /**
     * Gets the current cycle time in hours (0-24).
     * @return Current time of day in hours
     */
    fun getCycleTime(): Float {
        val dayNightEntity = scene.gameObjectManager.gameObjects.find {
            it.getComponent<DayNightCycleComponent>() != null
        }
        return dayNightEntity?.getComponent<DayNightCycleComponent>()?.cycleTime ?: 12f
    }

    /**
     * Sets the cycle time in hours (0-24).
     * @param time Time of day in hours
     */
    fun setCycleTime(time: Float) {
        val dayNightEntity = scene.gameObjectManager.gameObjects.find {
            it.getComponent<DayNightCycleComponent>() != null
        }
        dayNightEntity?.getComponent<DayNightCycleComponent>()?.cycleTime = time
    }

    override fun update(dt: Float) {
        // Find or create day/night cycle entity
        val dayNightEntity = scene.gameObjectManager.gameObjects.find {
            it.getComponent<DayNightCycleComponent>() != null
        }

        if (dayNightEntity == null) {
            // No day/night cycle entity exists - create one
            createDayNightCycleEntity()
            return
        }

        val dayNight = dayNightEntity.getComponent<DayNightCycleComponent>() ?: return

        // Get day duration (use override if provided)
        val dayDuration = dayDurationOverride ?: dayNight.dayDuration

        // Advance cycle time (convert dt to hours)
        val hoursPerSecond = 24f / dayDuration
        dayNight.cycleTime = (dayNight.cycleTime + dt * hoursPerSecond) % 24f

        // Compute sun direction from cycle time
        updateSunDirection(dayNight)

        // Interpolate sun color based on time of day
        updateSunColor(dayNight)

        // Update derived values
        dayNight.isDaytime = dayNight.cycleTime in 6f..18f
        dayNight.shadowIntensity = if (dayNight.isDaytime) 1f else 0.3f
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
    private fun updateSunDirection(dayNight: DayNightCycleComponent) {
        // Convert cycle time to angle (0-24 hours → 0-360 degrees)
        // Offset by 6 hours so noon = 0° (sun at zenith)
        val hoursFromNoon = dayNight.cycleTime - 12f
        val angleRadians = Math.toRadians(((hoursFromNoon / 24f) * 360f - 90f).toDouble())

        // Sun direction: Y component is sin (height), X component is cos (horizontal)
        dayNight.sunDirection.set(
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
    private fun updateSunColor(dayNight: DayNightCycleComponent) {
        val time = dayNight.cycleTime

        // Determine current phase and interpolation factor
        when {
            // Dawn: 5-7 hours (blend night → dawn → noon)
            time < 7f -> {
                val t = ((time - 5f) / 2f).coerceIn(0f, 1f)
                if (time < 6f) {
                    // Night to dawn
                    lerpColor(nightColor, dawnColor, t, dayNight.sunColor)
                } else {
                    // Dawn to noon
                    lerpColor(dawnColor, noonColor, t, dayNight.sunColor)
                }
                dayNight.sunIntensity = t
            }

            // Day: 7-17 hours (full brightness)
            time < 17f -> {
                dayNight.sunColor.set(noonColor)
                dayNight.sunIntensity = 1f
            }

            // Dusk: 17-19 hours (blend noon → dusk → night)
            time < 19f -> {
                val t = ((time - 17f) / 2f).coerceIn(0f, 1f)
                if (time < 18f) {
                    // Noon to dusk
                    lerpColor(noonColor, duskColor, t, dayNight.sunColor)
                } else {
                    // Dusk to night
                    lerpColor(duskColor, nightColor, t, dayNight.sunColor)
                }
                dayNight.sunIntensity = 1f - t
            }

            // Night: 19-5 hours (dark)
            else -> {
                dayNight.sunColor.set(nightColor)
                dayNight.sunIntensity = 0f
            }
        }

        // Compute ambient color (interpolates between night and day ambient)
        dayNight.ambientColor.set(nightAmbient).lerp(dayAmbient, dayNight.sunIntensity)
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

    /**
     * Creates a new entity with DayNightCycleComponent.
     * Called when no day/night cycle entity exists in the scene.
     */
    private fun createDayNightCycleEntity() {
        val entity = scene.gameObjectManager.createGameObject("DayNightCycle")
        entity.addComponent(DayNightCycleComponent())
    }
}
