package com.pafoid.skate.game.prefabs

import com.jme3.bounding.BoundingBox
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.bullet.BulletRigidBodyHandle
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.jme3.math.Vector3f as JmeVector3f
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * GPL-20: the collision Bullet actually builds for each library prefab (transform scale applied) fills the prefab's
 * visible mesh, so props neither sink into the ground nor float, and a dropped barrel settles upright on the floor.
 */
class PrefabColliderBoundsTest {

    private val debug = mockk<DebugRenderer>(relaxed = true)
    private val engine = mockk<Engine>(relaxed = true).also {
        every { it.assetsManager } returns mockk<AssetsManager>(relaxed = true)
        every { it.logger } returns LoggerService()
    }
    private val generator = PrefabsGenerator(engine, loadAssets = false)
    private lateinit var world: BulletPhysics3D

    @BeforeEach
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        world = BulletPhysics3D(debug)
    }

    @AfterEach
    fun tearDown() = world.destroy()

    /** Library prefabs that show a mesh and collide (players have their own rigs, see PrefabGeometryTest). */
    private val solidProps = PrefabCatalog.entries
        .filter { it.category != PrefabCategory.PLAYER }
        .map { it.type }
        .filter { type ->
            val go = generator.spawn(type)
            go.getComponent<RenderComponent>() != null && go.getComponent<RigidBody3D>()?.isTrigger == false
        }

    /** Mesh bounds of the prefab's model file (the engine copy of the bundled OBJ) in world space. */
    private fun meshBounds(type: PrefabType, go: GameObject): Pair<Vector3f, Vector3f> {
        val vertices = File(PrefabCatalog.entry(type).modelPath).readLines()
            .filter { it.startsWith("v ") }
            .map { line -> line.trim().split(Regex("\\s+")).let { Vector3f(it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) } }
        val t = go.getComponent<Transform>()!!
        assertEquals(Vector3f(), t.rotation, "$type: bounds check assumes an unrotated spawn")
        val world = vertices.map { Vector3f(it).mul(t.scale).add(t.translation) }
        return Vector3f(world.minOf { it.x }, world.minOf { it.y }, world.minOf { it.z }) to
            Vector3f(world.maxOf { it.x }, world.maxOf { it.y }, world.maxOf { it.z })
    }

    /**
     * World AABB of the body Bullet built (transform scale applied) and how far it may exceed the true collider: a lone
     * box or cylinder reports exact bounds; hulls and compounds are padded by their collision margins (3 x 0.04 m).
     */
    private fun colliderBounds(go: GameObject): Triple<Vector3f, Vector3f, Float> {
        world.add(go)
        val body = (go.getComponent<RigidBody3D>()!!.rawBody as BulletRigidBodyHandle).body
        val box = body.boundingBox(BoundingBox())
        val min = box.getMin(JmeVector3f())
        val max = box.getMax(JmeVector3f())
        val padding = if (body.collisionShape is CompoundCollisionShape) 0.125f else 0.01f
        return Triple(Vector3f(min.x, min.y, min.z), Vector3f(max.x, max.y, max.z), padding)
    }

    @Test
    fun libraryProps_ColliderBoundsMatchMeshBounds() {
        assertTrue(PrefabType.BARREL in solidProps && PrefabType.CRATE in solidProps)
        val mismatches = solidProps.mapNotNull { type ->
            val go = generator.spawn(type, Vector3f(2f, 0f, -3f))
            val (meshMin, meshMax) = meshBounds(type, go)
            val (colMin, colMax, tolerance) = colliderBounds(go)
            val axes = listOf("x" to 0, "y" to 1, "z" to 2).filter { (_, i) ->
                kotlin.math.abs(meshMin[i] - colMin[i]) > tolerance || kotlin.math.abs(meshMax[i] - colMax[i]) > tolerance
            }
            if (axes.isEmpty()) null
            else "$type ${axes.joinToString { it.first }}: mesh $meshMin..$meshMax, collider $colMin..$colMax"
        }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
    }

    @Test
    fun barrel_SpawnedOnFloor_RestsUprightWithoutRollingOrSinking() {
        world.add(GameObject("Floor")
            .addComponent(Transform(Vector3f(0f, -0.5f, 0f)))
            .addComponent(RigidBody3D(0f, bodyType = BodyType.Static))
            .addComponent(BoxCollider3D(Vector3f(20f, 0.5f, 20f))))
        val barrel = generator.spawn(PrefabType.BARREL, Vector3f(1f, 0f, 2f))
        val meshBottom = meshBounds(PrefabType.BARREL, barrel).first.y
        world.add(barrel)
        val body = barrel.getComponent<RigidBody3D>()!!.rawBody!!

        repeat(180) { world.step(1f / 60f) }

        val position = body.getLocation(Vector3f())
        val up = body.getRotation(org.joml.Quaternionf()).transform(Vector3f(0f, 1f, 0f))
        // Mesh bottom on the floor (y = 0): the body sits where it spawned, give or take the settle.
        assertEquals(0f, meshBottom + (position.y - barrel.getComponent<Transform>()!!.translation.y), 0.02f, "sank or floats")
        assertEquals(1f, position.x, 0.01f, "rolled along x")
        assertEquals(2f, position.z, 0.01f, "rolled along z")
        assertEquals(1f, up.y, 1e-3f, "tipped over")
        assertTrue(body.linearVelocity.length() < 0.01f, "still moving")
        assertTrue(body.angularVelocity.length() < 0.01f, "still spinning")
    }
}
