package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.terrain.TerrainScatterRenderer
import com.pafoid.skate.engine.terrain.Biome
import com.pafoid.skate.engine.terrain.ScatterInstance
import com.pafoid.skate.engine.terrain.ScatterRule
import com.pafoid.skate.engine.terrain.TerrainPreset
import io.mockk.mockk
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** PHY-27: static colliders for terrain scatter (REN-28 §7, task 7). */
class ScatterCollider3DTest {
    companion object {
        @JvmStatic
        fun backends(): List<PhysicsBackend> = PhysicsBackends.core() + PhysicsBackend.SIMPLE

        private fun box(min: Vector3f, max: Vector3f): List<Vector3f> = listOf(min.x, max.x).flatMap { x ->
            listOf(min.y, max.y).flatMap { y -> listOf(min.z, max.z).map { z -> Vector3f(x, y, z) } }
        }

        /** A 1 m rock standing on its base. */
        val ROCK = ColliderShape.TriangleMesh(box(Vector3f(-0.5f, 0f, -0.5f), Vector3f(0.5f, 1f, 0.5f)), listOf(0, 1, 2))

        /** A 0.4 m trunk, 3 m tall, under a 4 m wide canopy up to 5 m. */
        val TREE = ColliderShape.TriangleMesh(
            box(Vector3f(-0.2f, 0f, -0.2f), Vector3f(0.2f, 3f, 0.2f)) + box(Vector3f(-2f, 3f, -2f), Vector3f(2f, 5f, 2f)),
            listOf(0, 1, 2),
        )

        val MESHES = mapOf("rock.glb" to ROCK, "tree.glb" to TREE)
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

    /** Rule indices: 0 rock, 1 tree (both collide), 2 grass (does not). */
    private fun rules(density: Float = 0f) = listOf(
        ScatterRule(id = "rocks", model = "rock.glb", collider = true, densityPer100m2 = density, minSpacing = 3f),
        ScatterRule(id = "trees", model = "tree.glb", collider = true, densityPer100m2 = density, minSpacing = 5f),
        ScatterRule(id = "grass", model = "grass.glb", collider = false, densityPer100m2 = density, minSpacing = 1f),
    )

    /** A flat 32 m terrain (2 × 2 chunks of 16 m) with one biome holding [rules]. */
    private fun terrain(density: Float = 0f) = TerrainComponent(sizeMetres = 32f, sampleSpacing = 1f, chunkQuads = 16).apply {
        applyPreset(TerrainPreset.FLAT)
        biomes.clear()
        biomes += Biome(name = "Test", scatter = rules(density))
        runtime.ensureBuilt(key(projectSeed = 11L), parallel = false)
    }

    private fun item(rule: Int, x: Float, z: Float, yaw: Float = 0f, scale: Float = 1f) =
        ScatterInstance(rule, key = (x * 31 + z).toInt(), x = x, y = 0f, z = z, yaw = yaw, scale = scale, nx = 0f, ny = 1f, nz = 0f, random = 0.5f)

    /** Hand-placed items in the chunk holding (-8, -8): a rock at (-8, -8), a tree at (-8, -12) and grass at (-12, -8). */
    private fun TerrainComponent.placeItems(): TerrainComponent {
        runtime.chunk(0, 0)!!.scatter = listOf(item(0, -8f, -8f, yaw = 0.3f, scale = 1.2f), item(1, -8f, -12f), item(2, -12f, -8f))
        return this
    }

    private fun scatter() = ScatterCollider3D(physics) { MESHES[it] }

    private fun hitY(x: Float, z: Float): Float? = physics.raycast(Vector3f(x, 50f, z), Vector3f(x, -50f, z))?.point?.y

    // --- fitting and placement ---

    @Test
    fun fit_UsesTheBoundingBoxForRocks_AndTheTrunkForTrees() {
        val rock = ScatterCollider3D.fit(ROCK)!!
        assertEquals(Vector3f(0f, 0.5f, 0f), rock.center)
        assertEquals(Vector3f(0.5f), rock.halfExtents)

        val tree = ScatterCollider3D.fit(TREE)!!
        assertEquals(Vector3f(0f, 2.5f, 0f), tree.center)
        assertEquals(Vector3f(0.2f, 2.5f, 0.2f), tree.halfExtents)
    }

    @Test
    fun placement_MatchesTheRenderedInstance() {
        val renderer = TerrainScatterRenderer(mockk<ModelRenderer>(relaxed = true))
        val rule = ScatterRule(model = "rock.glb", alignToNormal = 0.6f)
        val normal = Vector3f(0.3f, 0.9f, -0.2f).normalize()
        val item = ScatterInstance(0, 1, 4f, 2f, -3f, 1.1f, 1.3f, normal.x, normal.y, normal.z, 0.5f)
        val terrainAt = Vector3f(10f, -1f, 5f)

        val rendered = renderer.matrixOf(item, rule, terrainAt).transformPosition(Vector3f(0.3f, 0.7f, -0.4f))
        val rotation = ScatterCollider3D.placement(item, rule, Quaternionf())
        val ours = rotation.transform(Vector3f(0.3f, 0.7f, -0.4f).mul(item.scale)).add(14f, 1f, 2f)
        assertEquals(0f, rendered.distance(ours), 1e-5f)
    }

    // --- bodies ---

    @ParameterizedTest
    @MethodSource("backends")
    fun collidingRules_GetABoxPerItem_AtTheRenderedPlace(backend: PhysicsBackend) {
        use(backend)
        val terrain = terrain().placeItems()
        val collider = scatter()
        collider.sync(terrain, Vector3f(0f, 2f, 0f))
        physics.step(dt)

        assertEquals(2, collider.bodyCount, "$backend: rock and tree, not grass")
        assertEquals(2f + 1.2f, hitY(-8f, -8f)!!, 0.05f, "$backend: top of the scaled rock")
        assertEquals(2f + 5f, hitY(-8f, -12f)!!, 0.05f, "$backend: top of the trunk box (full height)")
        assertNull(hitY(-9.5f, -12f), "$backend: under the canopy is free (no ground collider here)")
        assertNull(hitY(-12f, -8f), "$backend: grass does not collide")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun character_StopsAtARockAndATreeTrunk(backend: PhysicsBackend) {
        use(backend)
        val terrain = terrain().placeItems()
        HeightfieldCollider3D(physics).sync(terrain, Vector3f())
        scatter().sync(terrain, Vector3f())

        for ((target, label) in listOf(Vector3f(-8f, 0f, -8f) to "rock", Vector3f(-8f, 0f, -12f) to "tree")) {
            val character = physics.createRigidBody(ColliderShape.Capsule(0.3f, 1f, 1), 70f).apply {
                setLocation(Vector3f(target.x + 4f, 0.85f, target.z))
                setAngularFactor(Vector3f())
            }
            repeat(150) {
                // Walk towards the item at 3 m/s, like a controller driving the body
                character.linearVelocity = character.linearVelocity.also { it.x = -3f; it.z = 0f }
                physics.step(dt)
            }
            val at = character.getLocation(Vector3f())
            assertTrue(at.x > target.x + 0.4f, "$backend: walked through the $label, at $at")
            assertTrue(at.x < target.x + 1.6f, "$backend: stopped far from the $label, at $at")
            physics.removeRigidBody(character)
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun generatedScatter_GetsOneBodyPerCollidingItem(backend: PhysicsBackend) {
        use(backend)
        val terrain = terrain(density = 4f)
        val collider = scatter()
        collider.sync(terrain, Vector3f())

        val colliding = terrain.runtime.chunks.sumOf { c -> c.scatter.count { it.rule != 2 } }
        assertTrue(colliding > 5, "the rules placed items ($colliding)")
        assertEquals(colliding, collider.bodyCount, backend.name)
        val rock = terrain.runtime.chunks.flatMap { it.scatter }.first { it.rule == 0 }
        physics.step(dt)
        assertNotNull(hitY(rock.x, rock.z), "$backend: a generated rock collides")
    }

    // --- rebuilds ---

    @Test
    fun onlyAReplacedScatterList_RebuildsItsChunk_AndColliderOffRemovesAll() {
        use(PhysicsBackend.SIMPLE)
        val terrain = terrain().placeItems()
        val collider = scatter()
        collider.sync(terrain, Vector3f())
        assertEquals(4, collider.lastRebuilt)

        collider.sync(terrain, Vector3f())
        assertEquals(0, collider.lastRebuilt)

        val chunk = terrain.runtime.chunk(0, 0)!!
        chunk.scatter = chunk.scatter.take(1)
        collider.sync(terrain, Vector3f())
        assertEquals(1, collider.lastRebuilt)
        assertEquals(1, collider.bodyCount)

        collider.sync(terrain, Vector3f(5f, 0f, 0f))
        assertEquals(4, collider.lastRebuilt, "moved terrain re-places every chunk")
        physics.step(dt)
        assertNotNull(hitY(-3f, -8f), "the rock moved with the terrain")

        terrain.collider = false
        collider.sync(terrain, Vector3f(5f, 0f, 0f))
        assertEquals(0, collider.bodyCount)
        assertEquals(0, physics.rigidBodyCount)
    }

    @Test
    fun unavailableModel_GivesThatRuleNoColliders() {
        use(PhysicsBackend.SIMPLE)
        val terrain = terrain().placeItems()
        val collider = ScatterCollider3D(physics) { if (it == "rock.glb") null else MESHES[it] }
        collider.sync(terrain, Vector3f())
        assertEquals(1, collider.bodyCount, "only the tree")
    }
}
