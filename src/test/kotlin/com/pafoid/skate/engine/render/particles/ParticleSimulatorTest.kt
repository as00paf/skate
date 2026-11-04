package com.pafoid.skate.engine.render.particles

import com.pafoid.skate.engine.ecs.components.EmissionShape
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.acos
import kotlin.random.Random

class ParticleSimulatorTest {

    private val identity = Matrix4f()

    /** Deterministic emitter: zero-size box emitting straight up at a fixed speed and lifetime. */
    private fun emitter(configure: ParticleEmitter.() -> Unit = {}) = ParticleEmitter(
        shape = EmissionShape.BOX, emissionRate = 0f, lifetimeMin = 10f, lifetimeMax = 10f,
        speedMin = 2f, speedMax = 2f, seed = 42,
    ).apply { boxSize.zero() }.apply(configure)

    private fun started(e: ParticleEmitter) = e.runtime.also { it.restart(e) }

    @Test
    fun Emission_AccumulatesFractionalParticlesAcrossSteps() {
        val e = emitter { emissionRate = 10f }
        val rt = started(e)
        repeat(4) { ParticleSimulator.step(e, rt, 0.05f, identity) }
        assertEquals(2, rt.pool.count)
    }

    @Test
    fun Emission_BurstSpawnsOnceOnFirstStep() {
        val e = emitter { burstCount = 5; looping = false; duration = 10f }
        val rt = started(e)
        ParticleSimulator.step(e, rt, 0.1f, identity)
        ParticleSimulator.step(e, rt, 0.1f, identity)
        assertEquals(5, rt.pool.count)
    }

    @Test
    fun Emission_LoopingRepeatsBurstEachCycle() {
        val e = emitter { burstCount = 3; looping = true; duration = 1f }
        val rt = started(e)
        ParticleSimulator.step(e, rt, 0.6f, identity)
        ParticleSimulator.step(e, rt, 0.6f, identity) // crosses the cycle end
        ParticleSimulator.step(e, rt, 0.1f, identity)
        assertEquals(6, rt.pool.count)
    }

    @Test
    fun Emission_IsCappedByMaxParticles() {
        val e = emitter { emissionRate = 1000f; maxParticles = 8 }
        val rt = started(e)
        ParticleSimulator.step(e, rt, 1f, identity)
        assertEquals(8, rt.pool.count)
    }

    @Test
    fun Emission_NotPlayingEmitsNothing() {
        val e = emitter { emissionRate = 100f; burstCount = 4 }
        ParticleSimulator.step(e, e.runtime, 1f, identity)
        assertEquals(0, e.runtime.pool.count)
    }

    @Test
    fun NonLooping_StopsEmittingAfterDurationThenFinishes() {
        val e = emitter { emissionRate = 10f; looping = false; duration = 1f; lifetimeMin = 0.5f; lifetimeMax = 0.5f }
        val rt = started(e)
        repeat(11) { ParticleSimulator.step(e, rt, 0.1f, identity) }
        assertFalse(rt.playing)
        assertTrue(rt.pool.count > 0)
        repeat(10) { ParticleSimulator.step(e, rt, 0.1f, identity) }
        assertTrue(rt.isFinished)
    }

    @Test
    fun Lifetime_ParticlesDieWhenAgeReachesLife() {
        val e = emitter { burstCount = 3; looping = false; duration = 10f; lifetimeMin = 1f; lifetimeMax = 1f }
        val rt = started(e)
        ParticleSimulator.step(e, rt, 0.1f, identity)
        rt.playing = false
        ParticleSimulator.step(e, rt, 0.5f, identity)
        assertEquals(3, rt.pool.count)
        ParticleSimulator.step(e, rt, 0.5f, identity)
        assertEquals(0, rt.pool.count)
    }

    @Test
    fun Integration_AppliesVelocityAndGravity() {
        val e = emitter { burstCount = 1; gravity.set(0f, -10f, 0f) }
        val rt = started(e)
        ParticleSimulator.step(e, rt, 0.1f, identity) // spawn, not integrated yet
        rt.playing = false
        ParticleSimulator.step(e, rt, 0.1f, identity)
        // velocity 2 up, gravity adds -1 m/s this step: (2 - 1) * 0.1
        assertEquals(0.1f, rt.pool.posY[0], 1e-5f)
        assertEquals(-1f, rt.pool.accY[0], 1e-5f)
    }

    @Test
    fun Integration_EndSpeedScaleSlowsParticlesOverLife() {
        val e = emitter { burstCount = 1; endSpeedScale = 0f; lifetimeMin = 1f; lifetimeMax = 1f }
        val rt = started(e)
        ParticleSimulator.step(e, rt, 0.1f, identity)
        rt.playing = false
        ParticleSimulator.step(e, rt, 0.5f, identity) // t = 0.5 -> half speed
        assertEquals(0.5f, rt.pool.posY[0], 1e-5f)
    }

    @Test
    fun Spawn_UsesEmitterWorldTransform() {
        val e = emitter { burstCount = 1 }
        val rt = started(e)
        val world = Matrix4f().translate(5f, 1f, 0f).rotateZ(Math.toRadians(-90.0).toFloat())
        ParticleSimulator.step(e, rt, 0.1f, world)
        val p = rt.pool
        assertEquals(5f, p.posX[0], 1e-5f)
        assertEquals(1f, p.posY[0], 1e-5f)
        // local +Y rotated -90 degrees around Z points along +X
        assertEquals(2f, p.velX[0], 1e-4f)
        assertEquals(0f, p.velY[0], 1e-4f)
    }

    @Test
    fun Seed_SameSeedGivesSameParticles() {
        fun positions(): List<Float> {
            val e = emitter { shape = EmissionShape.SPHERE; shapeRadius = 3f; burstCount = 10 }
            val rt = started(e)
            ParticleSimulator.step(e, rt, 0.1f, identity)
            return (0 until rt.pool.count).flatMap { listOf(rt.pool.posX[it], rt.pool.posY[it], rt.pool.posZ[it]) }
        }
        assertEquals(positions(), positions())
    }

    @Test
    fun Shape_SphereSpawnsInsideRadiusMovingOutward() {
        val e = emitter { shape = EmissionShape.SPHERE; shapeRadius = 2f }
        val pos = Vector3f()
        val dir = Vector3f()
        val rng = Random(1)
        repeat(200) {
            ParticleSimulator.shapeSample(e, rng, pos, dir)
            assertTrue(pos.length() <= 2f + 1e-4f)
            assertEquals(1f, dir.length(), 1e-4f)
            assertTrue(pos.dot(dir) >= -1e-4f)
        }
    }

    @Test
    fun Shape_ConeDirectionsStayWithinAngle() {
        val e = emitter { shape = EmissionShape.CONE; coneAngle = 30f; shapeRadius = 1f }
        val pos = Vector3f()
        val dir = Vector3f()
        val rng = Random(2)
        repeat(200) {
            ParticleSimulator.shapeSample(e, rng, pos, dir)
            assertTrue(Math.toDegrees(acos(dir.y.toDouble())) <= 30.0 + 1e-3)
            assertEquals(0f, pos.y)
            assertTrue(pos.length() <= 1f + 1e-4f)
        }
    }

    @Test
    fun OverLife_SizeAndColorInterpolateFromStartToEnd() {
        val e = emitter {
            startSize = 1f; endSize = 3f
            startColor.set(1f, 0f, 0f, 1f); endColor.set(0f, 0f, 1f, 0f)
        }
        assertEquals(2f, ParticleSimulator.sizeAt(e, 0.5f), 1e-6f)
        val c = ParticleSimulator.colorAt(e, 0.25f, Vector4f())
        assertEquals(Vector4f(0.75f, 0f, 0.25f, 0.75f), c)
    }

    @Test
    fun Atlas_FrameAdvancesWithAgeAndMapsToUvRect() {
        val e = emitter { atlasColumns = 2; atlasRows = 2 }
        assertEquals(0, ParticleSimulator.frameAt(e, 0f))
        assertEquals(2, ParticleSimulator.frameAt(e, 0.6f))
        assertEquals(3, ParticleSimulator.frameAt(e, 1f))
        assertArrayEquals(floatArrayOf(0f, 0.5f, 0.5f, 0.5f), ParticleSimulator.uvRect(e, 2, FloatArray(4)))
        e.animateFrames = false
        assertEquals(0, ParticleSimulator.frameAt(e, 0.9f))
    }

    @Test
    fun Pool_KillMovesLastParticleIntoSlot() {
        val pool = ParticlePool(3)
        repeat(3) { pool.posX[pool.spawn()] = it.toFloat() }
        assertEquals(-1, pool.spawn())
        pool.kill(0)
        assertEquals(2, pool.count)
        assertEquals(2f, pool.posX[0])
        pool.ensureCapacity(1)
        assertEquals(1, pool.count)
    }
}
