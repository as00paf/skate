package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.asin
import kotlin.math.abs

/** REN-26: the day/night cycle drives the Sun entity and the environment's ambient light. */
class DayNightCycleSystemTest {
    private val eps = 1e-4f

    private fun scene(time: Float): Scene = SceneSun.addDefaults(Scene("S")).also {
        it.getComponent<DayNightCycleComponent>()!!.timeOfDay = time
    }

    private fun run(scene: Scene, dt: Float) = DayNightCycleSystem().also { it.init(scene) }.update(dt)

    @Test
    fun editMode_TimeStaysButTheSunFollowsIt() {
        val scene = scene(9f)
        run(scene, 10f)
        val cycle = scene.getComponent<DayNightCycleComponent>()!!
        assertEquals(9f, cycle.timeOfDay, eps, "time only advances in Play")
        val sun = SceneSun.of(scene)!!
        assertEquals(DayNightCycleSystem.sunDirection(9f, Vector3f()), sun.direction)
        assertEquals(1f, sun.intensity, eps)
    }

    @Test
    fun play_AdvancesTimeAndMovesTheSun() {
        val scene = scene(12f)
        scene.isRunning = true
        val cycle = scene.getComponent<DayNightCycleComponent>()!!
        run(scene, cycle.dayDuration / 4f) // a quarter day = 6 hours
        assertEquals(18f, cycle.timeOfDay, 1e-3f)
        assertEquals(DayNightCycleSystem.sunDirection(cycle.timeOfDay, Vector3f()), SceneSun.of(scene)!!.direction)
    }

    @Test
    fun sunDirection_NoonIsHighButNotStraightDown_HorizonAtSixAndEighteen() {
        val noon = DayNightCycleSystem.sunDirection(12f, Vector3f())
        assertEquals(1f, noon.length(), eps)
        val elevation = Math.toDegrees(asin(-noon.y.toDouble())).toFloat()
        assertEquals(90f - DayNightCycleSystem.SUN_ARC_TILT_DEGREES, elevation, 0.01f)
        assertTrue(abs(DayNightCycleSystem.sunDirection(6f, Vector3f()).y) < eps)
        assertTrue(abs(DayNightCycleSystem.sunDirection(18f, Vector3f()).y) < eps)
        assertTrue(DayNightCycleSystem.sunDirection(0f, Vector3f()).y > 0f, "below the horizon at midnight")
        val rise = DayNightCycleSystem.sunDirection(6f, Vector3f())
        assertEquals(-1f, rise.dot(DayNightCycleSystem.sunDirection(18f, Vector3f())), eps, "rises and sets on opposite sides")
        assertTrue(noon.x < -0.3f && noon.z < -0.3f, "noon light falls back-left like the default light (-1, -1, -1): $noon")
    }

    @Test
    fun daylight_RampsAtDawnAndDusk() {
        assertEquals(0f, DayNightCycleSystem.daylight(3f), eps)
        assertEquals(0.5f, DayNightCycleSystem.daylight(6f), eps)
        assertEquals(1f, DayNightCycleSystem.daylight(12f), eps)
        assertEquals(0.5f, DayNightCycleSystem.daylight(18f), eps)
        assertEquals(0f, DayNightCycleSystem.daylight(22f), eps)
    }

    @Test
    fun night_SunOffAndAmbientDarker() {
        val night = scene(23f)
        run(night, 0f)
        val day = scene(12f)
        run(day, 0f)
        assertEquals(0f, SceneSun.of(night)!!.intensity, eps)
        val nightAmbient = night.getComponent<EnvironmentComponent>()!!.ambient(Vector3f())
        val dayAmbient = day.getComponent<EnvironmentComponent>()!!.ambient(Vector3f())
        val cycle = DayNightCycleComponent()
        assertEquals(cycle.nightAmbient, nightAmbient)
        assertEquals(cycle.dayAmbient, dayAmbient)
    }

    @Test
    fun autoAmbientOff_LeavesTheEnvironmentAlone() {
        val scene = scene(23f)
        scene.getComponent<DayNightCycleComponent>()!!.autoAmbient = false
        run(scene, 0f)
        val env = scene.getComponent<EnvironmentComponent>()!!
        assertEquals(Vector3f(1f, 1f, 1f), env.ambientColor)
        assertEquals(EnvironmentComponent.DEFAULT_AMBIENT_INTENSITY, env.ambientIntensity, eps)
    }
}
