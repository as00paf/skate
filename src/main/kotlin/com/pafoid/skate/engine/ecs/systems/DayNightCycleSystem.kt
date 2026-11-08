package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drives the scene's sun ([SceneSun]) and ambient light from a [DayNightCycleComponent] (REN-26).
 *
 * Time only advances in Play; the sun direction, colour and intensity and (with [DayNightCycleComponent.autoAmbient])
 * the environment's ambient colour follow the time of day in the editor too, so changing the time moves the sun.
 */
class DayNightCycleSystem : System(priority = ExecutionPriority.EARLY) {

    override fun update(dt: Float) {
        val cycle = scene.getComponent<DayNightCycleComponent>()?.takeIf { it.enabled } ?: return
        if (scene.isRunning) {
            val hoursPerSecond = 24f / cycle.dayDuration
            cycle.timeOfDay = (cycle.timeOfDay + dt * hoursPerSecond) % 24f
        }
        apply(cycle, SceneSun.of(scene), scene.getComponent<EnvironmentComponent>())
    }

    companion object {
        /** Tilt of the sun's arc from the zenith: at noon the sun stands 90 - [SUN_ARC_TILT_DEGREES] up. */
        const val SUN_ARC_TILT_DEGREES = 35f

        /**
         * Turn of the arc around the vertical axis, so the noon light falls like the default directional light
         * (-1, -1, -1), only higher: shadows go back-left in the default views instead of hiding behind objects.
         */
        const val SUN_ARC_AZIMUTH_DEGREES = 45f

        /** Applies [cycle]'s time of day to [sun] and, with auto ambient, to [environment]'s ambient colour. */
        fun apply(cycle: DayNightCycleComponent, sun: DirectionalLightComponent?, environment: EnvironmentComponent?) {
            val time = cycle.timeOfDay
            cycle.isDaytime = time in 6f..18f
            cycle.shadowIntensity = if (cycle.isDaytime) 1f else 0.3f
            sun?.let {
                sunDirection(time, it.direction)
                it.intensity = sunColor(cycle, time, it.color)
            }
            if (cycle.autoAmbient && environment != null) {
                environment.ambientColor.set(cycle.nightAmbient).lerp(cycle.dayAmbient, daylight(time))
                environment.ambientIntensity = 1f
            }
        }

        /**
         * Direction the sunlight travels at [timeOfDay] (hours): down, [SUN_ARC_TILT_DEGREES] off vertical, at noon;
         * horizontal and opposite at 6:00 and 18:00; pointing up (below the horizon) at night. The arc is turned by
         * [SUN_ARC_AZIMUTH_DEGREES] around the vertical axis.
         */
        fun sunDirection(timeOfDay: Float, out: Vector3f): Vector3f {
            val angle = Math.toRadians((((timeOfDay - 12f) / 24f) * 360f - 90f).toDouble())
            val tilt = Math.toRadians(SUN_ARC_TILT_DEGREES.toDouble())
            val azimuth = Math.toRadians(SUN_ARC_AZIMUTH_DEGREES.toDouble())
            val x = cos(angle)
            val y = sin(angle)
            val z = y * sin(tilt)
            return out.set(
                (x * cos(azimuth) + z * sin(azimuth)).toFloat(),
                (y * cos(tilt)).toFloat(),
                (-x * sin(azimuth) + z * cos(azimuth)).toFloat(),
            ).normalize()
        }

        /** Sun strength 0..1 at [timeOfDay]: 0 at night, ramps 5-7 h and 17-19 h, 1 during the day. */
        fun daylight(timeOfDay: Float): Float = when {
            timeOfDay < 7f -> ((timeOfDay - 5f) / 2f).coerceIn(0f, 1f)
            timeOfDay < 17f -> 1f
            timeOfDay < 19f -> 1f - ((timeOfDay - 17f) / 2f).coerceIn(0f, 1f)
            else -> 0f
        }

        /** Writes the sun colour at [time] into [out] and returns its intensity ([daylight]). */
        private fun sunColor(cycle: DayNightCycleComponent, time: Float, out: Vector3f): Float {
            when {
                time < 6f -> out.set(cycle.nightColor).lerp(cycle.dawnColor, daylight(time))
                time < 7f -> out.set(cycle.dawnColor).lerp(cycle.noonColor, daylight(time))
                time < 17f -> out.set(cycle.noonColor)
                time < 18f -> out.set(cycle.noonColor).lerp(cycle.duskColor, 1f - daylight(time))
                time < 19f -> out.set(cycle.duskColor).lerp(cycle.nightColor, 1f - daylight(time))
                else -> out.set(cycle.nightColor)
            }
            return daylight(time)
        }
    }
}
