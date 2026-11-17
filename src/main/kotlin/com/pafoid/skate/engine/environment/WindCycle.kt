package com.pafoid.skate.engine.environment

import com.pafoid.skate.engine.ecs.components.EnvironmentComponent

/** Wind demo steps for a "cycle wind" button (REN-29 §11, Feature Showcase): calm → breeze → storm → calm. */
object WindCycle {
    enum class Step(val speed: Float, val gustStrength: Float, val turbulence: Float) {
        CALM(WindField.CALM, 0.2f, 0.1f),
        BREEZE(WindField.BREEZE, 0.4f, 0.3f),
        STORM(WindField.STORM, 0.6f, 0.8f),
    }

    /** The step closest to the current wind speed. */
    fun stepOf(env: EnvironmentComponent): Step = Step.entries.minBy { kotlin.math.abs(it.speed - env.windSpeed) }

    fun apply(env: EnvironmentComponent, step: Step) {
        env.windSpeed = step.speed
        env.windGustStrength = step.gustStrength
        env.windTurbulence = step.turbulence
    }

    /** Advances [env] to the next step and returns it. */
    fun next(env: EnvironmentComponent): Step {
        val all = Step.entries
        val next = all[(stepOf(env).ordinal + 1) % all.size]
        apply(env, next)
        return next
    }
}
