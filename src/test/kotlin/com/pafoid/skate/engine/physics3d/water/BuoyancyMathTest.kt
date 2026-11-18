package com.pafoid.skate.engine.physics3d.water

import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.ShapePart
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

/** PHY-16: buoyancy samples and the sphere-cap fraction (`docs/design/REN-29.md` §8.2, §12 tests). */
class BuoyancyMathTest {

    @Test
    fun sphereCapFraction_MatchesTheCapVolumeTable() {
        val r = 0.4f
        assertEquals(0f, BuoyancyMath.sphereCapFraction(-r, r))
        assertEquals(0f, BuoyancyMath.sphereCapFraction(-2f, r))
        assertEquals(0.5f, BuoyancyMath.sphereCapFraction(0f, r), 1e-6f)
        assertEquals(1f, BuoyancyMath.sphereCapFraction(r, r))
        // s = r/2: (r/2)² (3r − r/2) / 4r³ = 5/32
        assertEquals(5f / 32f, BuoyancyMath.sphereCapFraction(-r / 2f, r), 1e-6f)
        var previous = -1f
        for (i in -10..10) {
            val d = i * r / 10f
            val f = BuoyancyMath.sphereCapFraction(d, r)
            assertTrue(f >= previous, "monotonic at $d")
            assertEquals(1f, f + BuoyancyMath.sphereCapFraction(-d, r), 1e-5f, "symmetric at $d")
            previous = f
        }
    }

    @Test
    fun boxSamples_AddUpToItsVolume_AndUse27ForLargeBoxes() {
        val small = BuoyancyMath.samples(ColliderShape.Box(Vector3f(0.5f)))
        assertEquals(8, small.size)
        assertEquals(1f, BuoyancyMath.volume(small), 1e-5f)
        assertEquals(1f / 8f, small[0].volume, 1e-6f)
        assertEquals(BuoyancyMath.radiusFor(1f / 8f), small[0].radius, 1e-6f)

        val large = BuoyancyMath.samples(ColliderShape.Box(Vector3f(1.5f, 0.5f, 0.5f)))
        assertEquals(27, large.size)
        assertEquals(3f, BuoyancyMath.volume(large), 1e-4f)
    }

    @Test
    fun bodySamples_ApplyPartOffsetsAndScale() {
        val samples = BuoyancyMath.samples(listOf(ShapePart(ColliderShape.Box(Vector3f(0.5f)), Vector3f(0f, 1f, 0f))), Vector3f(2f))
        assertEquals(8f, BuoyancyMath.volume(samples), 1e-4f)
        val center = samples.fold(Vector3f()) { acc, s -> acc.add(s.local) }.div(samples.size.toFloat())
        assertEquals(0f, center.distance(0f, 2f, 0f), 1e-5f)
        assertEquals(BuoyancyMath.radiusFor(1f), samples[0].radius, 1e-5f)
    }

    @Test
    fun capsuleAndCylinderSamples_LieOnTheirAxis_WithTheShapeVolume() {
        val capsule = ColliderShape.Capsule(0.2f, 2f, 2)
        val samples = BuoyancyMath.samples(capsule)
        assertTrue(samples.all { it.local.x == 0f && it.local.y == 0f }, "along Z")
        val volume = (4.0 / 3.0 * PI).toFloat() * 0.008f + PI.toFloat() * 0.04f * 2f
        assertEquals(volume, BuoyancyMath.volume(samples), 1e-5f)

        val log = BuoyancyMath.samples(ColliderShape.Cylinder(0.3f, 3f, 0))
        assertTrue(log.all { it.local.y == 0f && it.local.z == 0f }, "along X")
        assertEquals(PI.toFloat() * 0.09f * 3f, BuoyancyMath.volume(log), 1e-4f)

        assertEquals(7, BuoyancyMath.samples(ColliderShape.Capsule(0.3f, 1e-3f, 1)).size, "a sphere")
    }

    @Test
    fun staticOnlyShapes_HaveNoSamples() {
        assertTrue(BuoyancyMath.samples(ColliderShape.TriangleMesh(listOf(Vector3f()), emptyList())).isEmpty())
        assertTrue(BuoyancyMath.samples(ColliderShape.Heightfield(2, 1f, FloatArray(4))).isEmpty())
    }

    /** Buoyant volume (m³) and torque about the centre of a body at [y] rotated by [rotation], water surface at y = 0. */
    private fun displaced(samples: List<BuoyancySample>, y: Float, rotation: Quaternionf, torque: Vector3f = Vector3f()): Float {
        var total = 0f
        samples.forEach { s ->
            val p = rotation.transform(Vector3f(s.local)).add(0f, y, 0f)
            val v = s.volume * BuoyancyMath.sphereCapFraction(-p.y, s.radius)
            total += v
            torque.add(Vector3f(p.x, 0f, p.z).cross(Vector3f(0f, v, 0f)))
        }
        return total
    }

    @Test
    fun halfDensityCube_DisplacesItsWeightAtHalfDepth() {
        val samples = BuoyancyMath.samples(ColliderShape.Box(Vector3f(0.5f)))
        assertEquals(0.5f, displaced(samples, 0f, Quaternionf()), 1e-5f, "half the volume under at half depth")
        assertTrue(displaced(samples, 0.2f, Quaternionf()) < 0.5f, "higher displaces less")
        assertTrue(displaced(samples, -0.2f, Quaternionf()) > 0.5f, "lower displaces more")
    }

    @Test
    fun tiltedPlank_GetsARightingTorque() {
        val samples = BuoyancyMath.samples(ColliderShape.Box(Vector3f(1f, 0.2f, 1f)))
        val torque = Vector3f()
        // Tilted +20° about Z: the righting torque turns it back, about −Z
        displaced(samples, 0f, Quaternionf().rotationZ(Math.toRadians(20.0).toFloat()), torque)
        assertTrue(torque.z < 0f, "righting torque $torque")
    }
}
