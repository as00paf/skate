package com.pafoid.skate.engine.ecs.config

import com.pafoid.skate.engine.ecs.components.EnvironmentComponent

/**
 * Fog demo steps for a "cycle fog" button (REN-09, FeatureShowcase): off → linear → exp → exp² → exp² with height fog
 * → off. Tuned for a scene a few tens of metres deep; only the fog curve fields change, the colour is kept.
 */
object FogCycle {
    enum class Step { OFF, LINEAR, EXP, EXP2, HEIGHT }

    fun stepOf(env: EnvironmentComponent): Step = when {
        !env.renderFog -> Step.OFF
        env.fogMode == FogMode.LINEAR -> Step.LINEAR
        env.fogMode == FogMode.EXP -> Step.EXP
        env.fogHeightFalloff > 0f -> Step.HEIGHT
        else -> Step.EXP2
    }

    fun apply(env: EnvironmentComponent, step: Step) {
        env.renderFog = step != Step.OFF
        when (step) {
            Step.OFF -> Unit
            Step.LINEAR -> { env.fogMode = FogMode.LINEAR; env.fogStart = 5f; env.fogEnd = 45f; env.fogHeightFalloff = 0f }
            Step.EXP -> { env.fogMode = FogMode.EXP; env.fogDensity = 0.04f; env.fogHeightFalloff = 0f }
            Step.EXP2 -> { env.fogMode = FogMode.EXP2; env.fogDensity = 0.035f; env.fogHeightFalloff = 0f }
            Step.HEIGHT -> { env.fogMode = FogMode.EXP2; env.fogDensity = 0.06f; env.fogHeight = 0f; env.fogHeightFalloff = 0.35f }
        }
    }

    /** Advances [env] to the next step and returns it. */
    fun next(env: EnvironmentComponent): Step {
        val all = Step.entries
        val next = all[(stepOf(env).ordinal + 1) % all.size]
        apply(env, next)
        return next
    }
}
