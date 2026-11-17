package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.terrain.TerrainPreset
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** PHY-26: chunk heightfield bodies for a generated terrain (REN-28 §8, task 9). */
class HeightfieldCollider3DTest {
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

    /** A 32 m rolling-hills terrain in 2 × 2 chunks of 16 quads, 1 m apart, about 0–8 m high. */
    private fun terrain() = TerrainComponent(sizeMetres = 32f, sampleSpacing = 1f, heightScaleMetres = 20f, chunkQuads = 16).apply {
        applyPreset(TerrainPreset.ROLLING_HILLS)
        runtime.ensureBuilt(key(projectSeed = 7L), parallel = false)
    }

    private fun hitY(x: Float, z: Float): Float? = physics.raycast(Vector3f(x, 200f, z), Vector3f(x, -200f, z))?.point?.y

    @ParameterizedTest
    @MethodSource("backends")
    fun generatedTerrain_GetsOneBodyPerChunk_AtTheRenderedHeights(backend: PhysicsBackend) {
        use(backend)
        val terrain = terrain()
        val position = Vector3f(100f, 5f, -20f)
        val collider = HeightfieldCollider3D(physics)
        collider.sync(terrain, position)
        physics.step(dt)

        assertEquals(4, collider.bodyCount)
        val field = terrain.runtime.heightField!!
        // On samples (exact on every backend), in all four chunks, on a shared chunk edge and near the far corner
        for ((x, z) in listOf(-15f to -15f, 3f to -9f, -7f to 12f, 9f to 9f, 0f to 4f, 15f to 15f)) {
            val y = hitY(position.x + x, position.z + z)
            assertNotNull(y, "$backend: no terrain at ($x, $z)")
            assertEquals(position.y + field.heightAt(x, z), y!!, 0.02f, "$backend: height at ($x, $z)")
        }
        assertNull(hitY(position.x + 16.5f, position.z), "$backend: beyond the terrain edge")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun bodiesAndCharacter_LandOnTheGeneratedTerrain(backend: PhysicsBackend) {
        use(backend)
        val terrain = terrain()
        HeightfieldCollider3D(physics).sync(terrain, Vector3f())
        val field = terrain.runtime.heightField!!
        val crate = physics.createRigidBody(ColliderShape.Box(Vector3f(0.4f)), 20f).apply {
            setLocation(Vector3f(-6.3f, field.heightAt(-6.3f, 5.2f) + 3f, 5.2f))
        }
        val character = physics.createRigidBody(ColliderShape.Capsule(0.3f, 1f, 1), 70f).apply {
            setLocation(Vector3f(7.7f, field.heightAt(7.7f, -8.1f) + 3f, -8.1f))
            setAngularFactor(Vector3f())
        }

        repeat(180) { physics.step(dt) }

        val c = character.getLocation(Vector3f())
        assertEquals(field.heightAt(c.x, c.z) + 0.8f, c.y, 0.12f, "$backend: character stands on the terrain at $c")
        val b = crate.getLocation(Vector3f())
        val ground = field.heightAt(b.x, b.z)
        assertTrue(b.y > ground && b.y < ground + 1.2f, "$backend: crate rests on the terrain at $b (ground $ground)")
    }

    @Test
    fun unchangedVersion_RebuildsNothing_AndAnEditRebuildsOnlyItsChunk() {
        use(PhysicsBackend.SIMPLE)
        val terrain = terrain()
        val collider = HeightfieldCollider3D(physics)
        collider.sync(terrain, Vector3f())
        assertEquals(4, collider.lastRebuilt)

        collider.sync(terrain, Vector3f())
        assertEquals(0, collider.lastRebuilt)

        // Sculpt an interior sample of chunk (1, 0); the renderer may already have cleared the dirty flag
        val chunk = terrain.runtime.chunk(1, 0)!!
        chunk.setHeight(5, 5, chunk.height(5, 5) + 3f)
        chunk.dirty = false
        terrain.runtime.markEdited()
        collider.sync(terrain, Vector3f())
        assertEquals(1, collider.lastRebuilt)
        physics.step(dt)
        assertEquals(chunk.height(5, 5), hitY(chunk.cornerX + 5f, chunk.cornerZ + 5f)!!, 1e-3f)
    }

    @Test
    fun editModeSync_WaitsForTheStrokeToSettle() {
        use(PhysicsBackend.SIMPLE)
        val terrain = terrain()
        val collider = HeightfieldCollider3D(physics)
        collider.sync(terrain, Vector3f(), waitForStableEdits = true)
        assertEquals(4, collider.bodyCount, "first build does not wait")

        val chunk = terrain.runtime.chunk(0, 0)!!
        chunk.setHeight(3, 3, 30f)
        terrain.runtime.markEdited()
        collider.sync(terrain, Vector3f(), waitForStableEdits = true)
        assertEquals(0, collider.lastRebuilt, "stroke still changing")
        collider.sync(terrain, Vector3f(), waitForStableEdits = true)
        assertEquals(1, collider.lastRebuilt, "rebuilt once the version held")
    }

    @Test
    fun movingTheTerrain_MovesItsBodies_AndColliderOffRemovesThem() {
        use(PhysicsBackend.SIMPLE)
        val terrain = terrain()
        val collider = HeightfieldCollider3D(physics)
        collider.sync(terrain, Vector3f())
        val field = terrain.runtime.heightField!!

        collider.sync(terrain, Vector3f(50f, 2f, 0f))
        assertEquals(0, collider.lastRebuilt, "moved, not rebuilt")
        assertEquals(2f + field.heightAt(3f, 3f), hitY(53f, 3f)!!, 0.02f)
        assertNull(hitY(3f, 3f), "old place is empty")

        terrain.collider = false
        collider.sync(terrain, Vector3f(50f, 2f, 0f))
        assertEquals(0, collider.bodyCount)
        assertEquals(0, physics.rigidBodyCount)
    }
}
