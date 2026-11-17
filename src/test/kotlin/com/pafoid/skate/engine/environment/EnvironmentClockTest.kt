package com.pafoid.skate.engine.environment

import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** REN-29 §5.3: one clock for wind, water and buoyancy. */
class EnvironmentClockTest {
    private val dt = 1f / 60f

    @Test
    fun Clock_InPlay_IsTickCountTimesFixedStep() {
        val clock = EnvironmentClock()
        repeat(120) { clock.step(dt, running = true, animateInEditor = false) }
        assertEquals(120L, clock.ticks)
        assertEquals(120 * dt, clock.animationTime, 1e-6f)
    }

    @Test
    fun Clock_StartingPlay_RestartsFromZero() {
        val clock = EnvironmentClock()
        repeat(30) { clock.step(dt, running = false, animateInEditor = true) }
        clock.step(dt, running = true, animateInEditor = true)
        assertEquals(1L, clock.ticks)
        assertEquals(dt, clock.animationTime, 1e-6f)
    }

    @Test
    fun Clock_InEditMode_PreviewsOnlyWhenAnimateInEditorIsOn() {
        val clock = EnvironmentClock()
        repeat(10) { clock.step(dt, running = false, animateInEditor = true) }
        assertEquals(10 * dt, clock.animationTime, 1e-6f)
        assertEquals(0L, clock.ticks)
        clock.stepAlpha = { 0.5f }
        clock.step(dt, running = false, animateInEditor = false)
        assertEquals(0f, clock.animationTime)
        assertEquals(0f, clock.renderTime(), "frozen at time 0, no interpolation")
    }

    @Test
    fun Clock_RenderTime_InterpolatesWithinTheFixedStep() {
        val clock = EnvironmentClock()
        clock.stepAlpha = { 0.25f }
        repeat(4) { clock.step(dt, running = true, animateInEditor = false) }
        assertEquals(4 * dt + 0.25f * dt, clock.renderTime(), 1e-6f)
        clock.stepAlpha = { 3f }
        assertEquals(5 * dt, clock.renderTime(), 1e-6f, "alpha is clamped to one step")
    }

    @Test
    fun Environment_Reset_RestoresWindDefaultsAndTheClock() {
        val env = EnvironmentComponent(windSpeed = 16f, windDirection = 10f, animateInEditor = false)
        env.runtime.step(dt, running = true, animateInEditor = false)
        env.reset()
        assertEquals(EnvironmentComponent.DEFAULT_WIND_SPEED, env.windSpeed)
        assertEquals(EnvironmentComponent.DEFAULT_WIND_DIRECTION, env.windDirection)
        assertEquals(true, env.animateInEditor)
        assertEquals(0f, env.runtime.animationTime)
    }

    @Test
    fun Environment_Wind_FollowsTheFields() {
        val env = EnvironmentComponent(windDirection = 90f, windSpeed = 8f)
        assertEquals(1f, env.wind().dirX, 1e-5f)
        assertEquals(8f, env.wind().speed)
        env.windSpeed = 0.5f
        assertEquals(0.5f, env.wind().speed)
    }

    @Test
    fun WindCycle_GoesCalmBreezeStormThenCalm() {
        val env = EnvironmentComponent(windSpeed = WindField.CALM)
        assertEquals(WindCycle.Step.BREEZE, WindCycle.next(env))
        assertEquals(WindField.BREEZE, env.windSpeed)
        assertEquals(WindCycle.Step.STORM, WindCycle.next(env))
        assertEquals(WindField.STORM, env.windSpeed)
        assertEquals(WindCycle.Step.CALM, WindCycle.next(env))
        assertEquals(WindField.CALM, env.windSpeed)
    }
}
