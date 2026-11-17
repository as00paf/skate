package com.pafoid.skate.engine.environment

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterPreset
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PHY-16: bounded-water queries (`docs/design/REN-29.md` §8.3–8.4). */
class WaterQueriesTest {
    private val queries = WaterQueries()

    private fun water(at: Vector3f, yawDeg: Float = 0f, configure: WaterComponent.() -> Unit = {}) = GameObject("Water").apply {
        addComponent(Transform(Vector3f(at)).also {
            it.worldMatrix.translation(at).rotateY(Math.toRadians(yawDeg.toDouble()).toFloat())
        })
        addComponent(WaterComponent(sizeX = 10f, sizeZ = 4f, depth = 2f, waves = mutableListOf()).apply(configure))
    }

    @Test
    fun sample_ReportsSurfaceFlowDensityAndFloor_InsideTheYawedRectangle() {
        // Turned 90°: the 10 m side runs along world Z, local +X flow becomes world −Z
        val pool = water(Vector3f(5f, 1f, 0f), yawDeg = 90f) { flowVelocity.set(2f, 0f); density = 1025f; linearDrag = 0.7f }
        queries.refresh(listOf(pool), null)

        val out = WaterSample()
        assertTrue(queries.sample(5.5f, 4.5f, 0f, out), "inside along the long side")
        assertEquals(1f, out.surfaceY, 1e-5f)
        assertEquals(-1f, out.floorY, 1e-5f)
        assertEquals(0f, out.flow.distance(0f, 0f, -2f), 1e-5f)
        assertEquals(1025f, out.density)
        assertEquals(0.7f, out.linearDrag)
        assertSame(pool, out.source)
        assertFalse(queries.sample(9f, 0f, 0f, out), "outside the short side after the yaw")
    }

    @Test
    fun surface_FollowsTheSameWavesAsTheRenderer() {
        val env = EnvironmentComponent().apply { windSpeed = 6f; windDirection = 40f }
        val pool = water(Vector3f(0f, 2f, 0f)) { waves.addAll(WaterPreset.LAKE.waves()); windDrivenWaves = true }
        queries.refresh(listOf(pool), env)

        val reference = WaterWaves().configure(WaterPreset.LAKE.waves(), true, 6f, 40f)
        val out = WaterSample()
        queries.sample(1.3f, -0.7f, 3.2f, out)
        assertEquals(2f + reference.heightAt(1.3f, -0.7f, 3.2f), out.surfaceY, 1e-5f)
    }

    @Test
    fun volumeAt_CoversFloorToHighestCrest() {
        val pool = water(Vector3f()) { waves.add(GerstnerWave(amplitude = 0.3f, wavelength = 10f)) }
        queries.refresh(listOf(pool), null)
        assertSame(pool, queries.volumeAt(0f, -1.9f, 0f)?.source)
        assertSame(pool, queries.volumeAt(0f, 0.29f, 0f)?.source)
        assertNull(queries.volumeAt(0f, 0.4f, 0f))
        assertNull(queries.volumeAt(0f, -2.1f, 0f))
    }

    @Test
    fun submersion_GivesFeetCentreAndHeadDepths() {
        queries.refresh(listOf(water(Vector3f())), null)
        val out = Submersion()
        // Capsule radius 0.3, height 1.2: 1.8 m tall, centre 0.5 m under
        assertTrue(queries.submersion(Vector3f(0f, -0.5f, 0f), 0.3f, 1.2f, 0f, out))
        assertEquals(1.4f, out.feetDepth, 1e-5f)
        assertEquals(0.5f, out.centerDepth, 1e-5f)
        assertEquals(-0.4f, out.headDepth, 1e-5f)
        assertEquals(1.4f / 1.8f, out.fraction, 1e-5f)
        assertFalse(queries.submersion(Vector3f(20f, 0f, 0f), 0.3f, 1.2f, 0f, out))
    }

    @Test
    fun disabledWater_IsIgnored() {
        val pool = water(Vector3f())
        pool.getComponent<WaterComponent>()!!.enabled = false
        queries.refresh(listOf(pool), null)
        assertTrue(queries.volumes.isEmpty())
    }
}
