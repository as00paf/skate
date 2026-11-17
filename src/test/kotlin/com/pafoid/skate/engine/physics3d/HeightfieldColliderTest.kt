package com.pafoid.skate.engine.physics3d

import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** PHY-15: heightfield terrain collider on every backend (`docs/design/REN-28.md` §8). */
class HeightfieldColliderTest {
    companion object {
        @JvmStatic
        fun backends(): List<PhysicsBackend> = PhysicsBackends.core() + PhysicsBackend.SIMPLE
    }

    private lateinit var physics: IPhysics3D
    private val dt = 1f / 60f

    private fun use(backend: PhysicsBackend) {
        physics = PhysicsBackends.create(backend)
    }

    @AfterEach
    fun tearDown() {
        if (::physics.isInitialized) physics.destroy()
    }

    private fun field(n: Int, spacing: Float, height: (Float, Float) -> Float) = ColliderShape.Heightfield(
        n, spacing, FloatArray(n * n) { i -> height((i % n) * spacing, (i / n) * spacing) },
    )

    /** One static terrain chunk with its corner sample at [corner]. */
    private fun terrain(shape: ColliderShape.Heightfield, corner: Vector3f): IRigidBodyHandle =
        physics.createRigidBody(shape, 0f).also { it.setLocation(corner) }

    private fun hitY(x: Float, z: Float): Float? =
        physics.raycast(Vector3f(x, 50f, z), Vector3f(x, -50f, z))?.point?.y

    // --- shape ---

    @Test
    fun heightfield_RejectsWrongSampleCounts_AndComparesByContent() {
        assertThrows(IllegalArgumentException::class.java) { ColliderShape.Heightfield(3, 1f, FloatArray(8)) }
        assertThrows(IllegalArgumentException::class.java) { ColliderShape.Heightfield(1, 1f, FloatArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { ColliderShape.Heightfield(2, 0f, FloatArray(4)) }
        assertThrows(IllegalArgumentException::class.java) { ColliderShape.Heightfield(2, 1f, floatArrayOf(0f, 0f, Float.NaN, 0f)) }

        val a = ColliderShape.Heightfield(2, 1f, floatArrayOf(0f, 1f, 2f, -3f))
        assertEquals(a, ColliderShape.Heightfield(2, 1f, floatArrayOf(0f, 1f, 2f, -3f)))
        assertEquals(a.hashCode(), ColliderShape.Heightfield(2, 1f, floatArrayOf(0f, 1f, 2f, -3f)).hashCode())
        assertNotEquals(a, ColliderShape.Heightfield(2, 1f, floatArrayOf(0f, 1f, 2f, 3f)))
        assertEquals(-3f, a.minY)
        assertEquals(2f, a.maxY)
        assertEquals(2f, a.height(0, 1))
    }

    @Test
    fun heightfieldWireframe_DrawsTheGridLinesWithoutDiagonals() {
        val edges = ShapeWireframe.heightfieldEdges(field(3, 2f) { x, z -> x + z })
        assertEquals(2 * 3 * 2 * 6, edges.size)
        // First row segment: sample (0, 0) to (1, 0)
        assertEquals(listOf(0f, 0f, 0f, 2f, 2f, 0f), edges.take(6))
    }

    // --- queries ---

    @ParameterizedTest
    @MethodSource("backends")
    fun raycast_HitsTheSurfaceBetweenSamples_RelativeToTheCornerSample(backend: PhysicsBackend) {
        use(backend)
        val corner = Vector3f(10f, 2f, -5f)
        terrain(field(9, 2f) { x, z -> 0.25f * x + 0.1f * z }, corner)
        physics.step(dt)

        val hit = physics.raycast(Vector3f(15.3f, 50f, 2.1f), Vector3f(15.3f, -50f, 2.1f))
        assertNotNull(hit, "$backend: ray should hit the terrain")
        assertEquals(2f + 0.25f * 5.3f + 0.1f * 7.1f, hit!!.point.y, 0.02f, "$backend: height")
        val expected = Vector3f(-0.25f, 1f, -0.1f).normalize()
        assertTrue(hit.normal.angle(expected) < 0.02f, "$backend: normal ${hit.normal} vs $expected")

        assertNull(hitY(9f, 0f), "$backend: west of the terrain")
        assertNull(hitY(27f, 0f), "$backend: east of the terrain (size 16 m)")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun quads_SplitAlongTheSameDiagonal_OnEveryBackend(backend: PhysicsBackend) {
        use(backend)
        // Only sample (1, 0) is raised. With the (0,0)–(1,1) diagonal the point (0.2, 0.6) lies in the flat triangle;
        // with the other diagonal it would read 0.2.
        terrain(ColliderShape.Heightfield(3, 1f, FloatArray(9).also { it[1] = 1f }), Vector3f())
        physics.step(dt)

        assertEquals(0f, hitY(0.2f, 0.6f)!!, 0.01f, "$backend: flat triangle")
        assertEquals(0.6f, hitY(0.8f, 0.2f)!!, 0.01f, "$backend: raised triangle (x - z)")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun terrainChunk_With65SamplesPerSide_CollidesUpToItsFarEdge(backend: PhysicsBackend) {
        use(backend)
        terrain(field(65, 1f) { _, _ -> 3f }, Vector3f(-32f, 0f, -32f))
        physics.step(dt)

        assertEquals(3f, hitY(-31.5f, -31.5f)!!, 0.01f, "$backend: near corner")
        assertEquals(3f, hitY(31.5f, 31.5f)!!, 0.01f, "$backend: far corner (Jolt pads 65 to its block size)")
        assertNull(hitY(32.5f, 0f), "$backend: no collision in the padding")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun removedTerrain_NoLongerHit(backend: PhysicsBackend) {
        use(backend)
        val chunk = terrain(field(5, 1f) { _, _ -> 0f }, Vector3f())
        physics.step(dt)
        assertNotNull(hitY(2f, 2f))

        physics.removeRigidBody(chunk)
        physics.step(dt)
        assertNull(hitY(2f, 2f), "$backend: removed chunk")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun shapeCast_StopsACapsuleOnTheTerrain(backend: PhysicsBackend) {
        use(backend)
        terrain(field(9, 1f) { _, _ -> 2f }, Vector3f())
        physics.step(dt)

        val capsule = ColliderShape.Capsule(0.3f, 1f, 1)
        val hit = physics.shapeCast(capsule, Vector3f(4f, 10f, 4f), Vector3f(4f, -10f, 4f))
        assertNotNull(hit, "$backend: cast should hit the terrain")
        assertEquals(2f + 0.8f, hit!!.point.y, 0.06f, "$backend: capsule centre at impact")
    }

    // --- simulation ---

    @ParameterizedTest
    @MethodSource("backends")
    fun dynamicHeightfield_IsSimulatedAsStatic(backend: PhysicsBackend) {
        use(backend)
        val chunk = physics.createRigidBody(field(3, 1f) { _, _ -> 0f }, 50f)
        repeat(30) { physics.step(dt) }
        assertEquals(0f, chunk.getLocation(Vector3f()).y, 1e-4f, "$backend: terrain must not fall")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun characterCapsule_LandsAndRestsOnTheTerrain(backend: PhysicsBackend) {
        use(backend)
        // Bumpy away from the landing spot, flat at 1 m around it
        terrain(field(17, 1f) { x, z -> if (x in 6f..10f && z in 6f..10f) 1f else 1f + 0.3f * ((x + z) % 3f) }, Vector3f())
        val character = physics.createRigidBody(ColliderShape.Capsule(0.3f, 1f, 1), 70f).apply {
            setLocation(Vector3f(8f, 4f, 8f))
            setAngularFactor(Vector3f())
        }

        repeat(120) { physics.step(dt) }

        val at = character.getLocation(Vector3f())
        assertEquals(1f + 0.8f, at.y, 0.05f, "$backend: capsule rests on the ground")
        assertTrue(character.linearVelocity.length() < 0.1f, "$backend: at rest, v=${character.linearVelocity}")
        assertNotNull(physics.raycast(Vector3f(at), Vector3f(at).sub(0f, 2f, 0f), excludeBody = null), "$backend: ground ray")
    }

    /** Design §8: neighbouring chunks share edge samples, so a body crosses the seam without a bump. */
    @ParameterizedTest
    @MethodSource("backends")
    fun rollingAcrossAChunkSeam_HasNoBump(backend: PhysicsBackend) {
        use(backend)
        val slope = 0.2f
        val height = { worldX: Float -> -slope * worldX }
        terrain(field(5, 2f) { x, _ -> height(x) }, Vector3f(0f, 0f, 0f))
        terrain(field(5, 2f) { x, _ -> height(8f + x) }, Vector3f(8f, 0f, 0f))
        val radius = 0.25f
        // A capsule lying along Z rolls down the X slope
        val log = physics.createRigidBody(ColliderShape.Capsule(radius, 1f, 2), 1f).apply {
            setLocation(Vector3f(4f, height(4f) + radius + 0.02f, 4f))
            setRotation(Quaternionf())
        }

        val at = Vector3f()
        var maxUp = Float.NEGATIVE_INFINITY
        var lowest = Float.POSITIVE_INFINITY
        var steps = 0
        while (steps < 600 && log.getLocation(at).x < 11f) {
            physics.step(dt)
            steps++
            if (steps < 30) continue // settling after the drop
            log.getLocation(at)
            if (at.x in 6f..10f) maxUp = maxOf(maxUp, log.linearVelocity.y)
            lowest = minOf(lowest, at.y - height(at.x))
        }

        assertTrue(at.x >= 11f, "$backend: should roll past the seam at x=8, stopped at $at")
        assertTrue(maxUp < 0.05f, "$backend: upward kick of $maxUp m/s near the seam")
        assertTrue(lowest > radius - 0.05f, "$backend: sank into the terrain (clearance $lowest)")
    }
}
