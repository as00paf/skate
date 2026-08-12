package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

class DayNightCycleSystem : System(priority = ExecutionPriority.EARLY) {

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        val cycleCmp = scene.getComponent<DayNightCycleComponent>()?.takeIf { it.enabled } ?: return
        val dirLightCmp = scene.getComponent<DirectionalLightComponent>()?.takeIf { it.enabled }
        val ambientLightCmp = scene.getComponent<DirectionalLightComponent>()?.takeIf { it.enabled }

        // Get day duration (use override if provided)
        val dayDuration = cycleCmp.dayDuration

        // Advance cycle time (convert dt to hours)
        val hoursPerSecond = 24f / dayDuration
        cycleCmp.timeOfDay = (cycleCmp.timeOfDay + dt * hoursPerSecond) % 24f

        // Compute sun direction from cycle time
        dirLightCmp?.let {
            updateSunDirection(cycleCmp, it)
            updateSunColor(cycleCmp, it)
        }

        // Update derived values
        cycleCmp.isDaytime = cycleCmp.timeOfDay in 6f..18f
        cycleCmp.shadowIntensity = if (cycleCmp.isDaytime) 1f else 0.3f

        // Update scene ambient light if auto mode is enabled
        if (cycleCmp.autoAmbient && ambientLightCmp != null) {
            ambientLightCmp.color.set(cycleCmp.nightAmbient).lerp(cycleCmp.dayAmbient, ambientLightCmp.intensity)
        }
    }

    private fun updateSunDirection(cycleCmp: DayNightCycleComponent, dirLightCmp: DirectionalLightComponent) {
        // Convert cycle time to angle (0-24 hours → 0-360 degrees)
        // Offset by 6 hours so noon = 0° (sun at zenith)
        val hoursFromNoon = cycleCmp.timeOfDay - 12f
        val angleRadians = Math.toRadians(((hoursFromNoon / 24f) * 360f - 90f).toDouble())

        // Sun direction: Y component is sin (height), X component is cos (horizontal)
        dirLightCmp.direction.set(
            cos(angleRadians).toFloat(),
            sin(angleRadians).toFloat(),
            0f
        ).normalize()
    }

    private fun updateSunColor(cycleCmp: DayNightCycleComponent, dirLightCmp: DirectionalLightComponent) {
        val time = cycleCmp.timeOfDay
        var intensity: Float

        // Determine current phase and interpolation factor
        when {
            // Dawn: 5-7 hours (blend night → dawn → noon)
            time < 7f -> {
                intensity = ((time - 5f) / 2f).coerceIn(0f, 1f)
                if (time < 6f) {
                    // Night to dawn
                    lerpColor(cycleCmp.nightColor, cycleCmp.dawnColor, intensity, dirLightCmp.color)
                } else {
                    // Dawn to noon
                    lerpColor(cycleCmp.dawnColor, cycleCmp.noonColor, intensity, dirLightCmp.color)
                }
                dirLightCmp.intensity = intensity
            }

            // Day: 7-17 hours (full brightness)
            time < 17f -> {
                dirLightCmp.color.set(cycleCmp.noonColor)
                dirLightCmp.intensity = 1f
            }

            // Dusk: 17-19 hours (blend noon → dusk → night)
            time < 19f -> {
                intensity = ((time - 17f) / 2f).coerceIn(0f, 1f)
                if (time < 18f) {
                    // Noon to dusk
                    lerpColor(cycleCmp.noonColor, cycleCmp.duskColor, intensity, dirLightCmp.color)
                } else {
                    // Dusk to night
                    lerpColor(cycleCmp.duskColor, cycleCmp.nightColor, intensity, dirLightCmp.color)
                }
                dirLightCmp.intensity = 1f - intensity
            }

            // Night: 19-5 hours (dark)
            else -> {
                dirLightCmp.color.set(cycleCmp.nightColor)
                dirLightCmp.intensity = 0f
            }
        }
    }

    private fun lerpColor(from: Vector3f, to: Vector3f, t: Float, result: Vector3f) {
        result.set(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.z + (to.z - from.z) * t
        )
    }
}
