package com.pafoid.skate.engine.render.fog

import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.config.FogMode
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.exp

/**
 * CPU mirror of `shaders/fog.glsl` (REN-09), used by tests and anything that needs the fog amount on the CPU.
 * Fog amount is 0 (clear) to 1 (only fog colour).
 */
object FogMath {
    /** Value of the shader's `uFogMode`: 0 when fog is off or has no effect. */
    fun shaderMode(env: EnvironmentComponent?): Int {
        if (env == null || !env.enabled || !env.renderFog) return 0
        return when (env.fogMode) {
            FogMode.LINEAR -> if (env.fogEnd > env.fogStart) 1 else 0
            FogMode.EXP -> if (env.fogDensity > 0f) 2 else 0
            FogMode.EXP2 -> if (env.fogDensity > 0f) 3 else 0
        }
    }

    fun distanceAmount(mode: FogMode, d: Float, density: Float, start: Float, end: Float): Float = when (mode) {
        FogMode.LINEAR -> ((d - start) / maxOf(end - start, 1e-4f)).coerceIn(0f, 1f)
        FogMode.EXP -> 1f - exp(-density * d)
        FogMode.EXP2 -> (density * d).let { 1f - exp(-it * it) }
    }

    /** Ray-averaged height density between [camera] and [world], capped at 1; 1 when [falloff] is 0 (no height fog). */
    fun heightWeight(camera: Vector3f, world: Vector3f, fogHeight: Float, falloff: Float): Float {
        if (falloff <= 0f) return 1f
        val t = falloff * (world.y - camera.y)
        val ray = if (abs(t) < 1e-4f) 1f else (1f - exp(-t)) / t
        return (exp(-falloff * (camera.y - fogHeight)) * ray).coerceIn(0f, 1f)
    }

    /** Fog amount at [world] seen from [camera] with [env]'s settings (0 when fog is off). */
    fun amount(env: EnvironmentComponent?, camera: Vector3f, world: Vector3f): Float {
        if (shaderMode(env) == 0) return 0f
        env!!
        val d = camera.distance(world)
        return (distanceAmount(env.fogMode, d, env.fogDensity, env.fogStart, env.fogEnd) *
            heightWeight(camera, world, env.fogHeight, env.fogHeightFalloff)).coerceIn(0f, 1f)
    }
}
