package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.collision.shapes.MeshCollisionShape
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.Collider3D
import com.pafoid.skate.engine.ecs.components.CompoundHullCollider3D
import com.pafoid.skate.engine.ecs.components.CustomCollider3D
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.physics3d.simple.SimplePhysics3D
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabType
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** PHY-13: compound-hull and static triangle-mesh colliders. */
class CompoundAndMeshColliderTest {

    private val debug = mockk<DebugRenderer>(relaxed = true)
    private lateinit var world: BulletPhysics3D

    @BeforeEach
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        world = BulletPhysics3D(debug)
    }

    @AfterEach
    fun tearDown() = world.destroy()

    private fun cube(half: Float, center: Vector3f = Vector3f()) = listOf(-1f, 1f).flatMap { x ->
        listOf(-1f, 1f).flatMap { y -> listOf(-1f, 1f).map { z -> Vector3f(x, y, z).mul(half).add(center) } }
    }

    private fun body(vararg colliders: Collider3D, mass: Float = 0f, type: BodyType = BodyType.Static, at: Vector3f = Vector3f()) =
        GameObject("Body").apply {
            addComponent(Transform(Vector3f(at)))
            addComponent(RigidBody3D(mass, bodyType = type))
            colliders.forEach { attachComponent(it as com.pafoid.skate.engine.ecs.components.Component) }
        }

    private fun shapeOf(go: GameObject) = world.bodyShape(BodyDescription.parts(go.components.filterIsInstance<Collider3D>()))

    // --- components ---

    @Test
    fun compoundHull_DropsHullsWithTooFewPoints_AndFallsBackToABox() {
        val shape = CompoundHullCollider3D(listOf(cube(1f), listOf(Vector3f(), Vector3f(1f)))).toShape()
        assertEquals(1, (shape as ColliderShape.CompoundHull).hulls.size)

        assertInstanceOf(ColliderShape.Box::class.java, CompoundHullCollider3D(listOf(listOf(Vector3f()))).toShape())
    }

    @Test
    fun meshCollider_FromTriangles_SharesVertices_AndDropsBrokenTriangles() {
        val a = Vector3f(0f, 0f, 0f); val b = Vector3f(1f, 0f, 0f); val c = Vector3f(1f, 0f, 1f); val d = Vector3f(0f, 0f, 1f)
        val mesh = MeshCollider3D.fromTriangles(listOf(Triple(a, b, c), Triple(a, c, d)))
        assertEquals(4, mesh.vertices.size)
        assertEquals(listOf(0, 1, 2, 0, 2, 3), mesh.indices)

        val broken = MeshCollider3D(mesh.vertices, listOf(0, 1, 2, 0, 9, 3, 1)).toShape() as ColliderShape.TriangleMesh
        assertEquals(listOf(0, 1, 2), broken.indices)
    }

    @Test
    fun serialization_RoundTripsBothColliders() {
        val hull = CompoundHullCollider3D(listOf(cube(1f), cube(0.5f, Vector3f(3f, 0f, 0f))), offset = Vector3f(0f, 1f, 0f))
        val mesh = MeshCollider3D(listOf(Vector3f(0f), Vector3f(1f, 0f, 0f), Vector3f(0f, 0f, 1f)), listOf(0, 2, 1), Vector3f(2f))
        val scene = Scene("S").apply {
            addChild(GameObject("Hull").addComponent(hull))
            addChild(GameObject("Mesh").addComponent(mesh))
        }
        val serializer = Serializer()

        val back = serializer.decode<Scene>(serializer.encode(scene))

        val hullBack = back.children.first { it.name == "Hull" }.getComponent<CompoundHullCollider3D>()!!
        assertEquals(hull.hulls, hullBack.hulls)
        assertEquals(hull.offset, hullBack.offset)
        val meshBack = back.children.first { it.name == "Mesh" }.getComponent<MeshCollider3D>()!!
        assertEquals(mesh.vertices, meshBack.vertices)
        assertEquals(mesh.indices, meshBack.indices)
        assertEquals(mesh.offset, meshBack.offset)
    }

    // --- Bullet shape building ---

    @Test
    fun bullet_LoneCompoundHull_IsOneCompoundWithAChildPerHull() {
        val shape = shapeOf(body(CompoundHullCollider3D(listOf(cube(1f), cube(1f, Vector3f(3f, 0f, 0f)), cube(1f, Vector3f(6f, 0f, 0f))))))

        assertEquals(3, (shape as CompoundCollisionShape).countChildren())
    }

    @Test
    fun bullet_CompoundHullBesideOtherColliders_IsFlattenedNotNested() {
        val shape = shapeOf(body(
            CompoundHullCollider3D(listOf(cube(1f), cube(1f, Vector3f(3f, 0f, 0f))), offset = Vector3f(0f, 2f, 0f)),
            BoxCollider3D(Vector3f(0.5f)),
        ))

        val compound = shape as CompoundCollisionShape
        assertEquals(3, compound.countChildren())
        assertTrue(compound.listChildren().none { it.shape is CompoundCollisionShape })
        assertEquals(2, compound.listChildren().count { it.copyOffset(null).y == 2f })
    }

    @Test
    fun bullet_MeshCollider_BuildsATriangleMesh() {
        val mesh = MeshCollider3D.fromTriangles(PrefabCatalog.shape(PrefabType.BANK)!!.mesh.triangles())

        val shape = shapeOf(body(mesh))

        assertEquals(mesh.indices.size / 3, (shape as MeshCollisionShape).countMeshTriangles())
    }

    @Test
    fun meshCollider_OnDynamicOrKinematicBody_IsSimulatedStatic() {
        val dynamic = body(MeshCollider3D(), mass = 5f, type = BodyType.Dynamic)
        val kinematic = body(MeshCollider3D(), mass = 5f, type = BodyType.Kinematic)
        world.add(dynamic)
        world.add(kinematic)
        world.add(dynamic) // re-add: the mass check must not rebuild it forever

        listOf(dynamic, kinematic).forEach { go ->
            val handle = go.getComponent<RigidBody3D>()!!.rawBody!!
            assertEquals(0f, handle.mass)
            assertTrue(!handle.isKinematic)
        }
        assertEquals(2, world.rigidBodyCount)

        val simple = SimplePhysics3D()
        val go = body(MeshCollider3D(), mass = 5f, type = BodyType.Dynamic)
        simple.add(go)
        assertEquals(0f, go.getComponent<RigidBody3D>()!!.rawBody!!.mass)
    }

    @Test
    fun bullet_BoxComesToRestOnMeshAndHullDecks() {
        val bank = PrefabCatalog.shape(PrefabType.BANK)!!
        val points = bank.hulls.flatten()
        val deckTop = points.maxOf { it.y }
        val deck = points.filter { it.y == deckTop }.fold(Vector3f()) { acc, p -> acc.add(p) }.div(points.count { it.y == deckTop }.toFloat())
        listOf(
            body(MeshCollider3D.fromTriangles(bank.mesh.triangles())),
            body(CompoundHullCollider3D(bank.hulls), at = Vector3f(10f, 0f, 0f)),
        ).forEach { world.add(it) }
        val boxes = listOf(0f, 10f).map { x ->
            body(BoxCollider3D(Vector3f(0.2f)), mass = 1f, type = BodyType.Dynamic, at = Vector3f(x + deck.x, deckTop + 1f, deck.z))
                .also { world.add(it) }
        }

        repeat(120) { world.step(1f / 60f) }

        boxes.forEach { box ->
            val y = box.getComponent<RigidBody3D>()!!.rawBody!!.getLocation(Vector3f()).y
            assertEquals(deckTop + 0.2f, y, 0.06f, "box at x=${box.getComponent<Transform>()!!.translation.x} fell through the deck")
        }
    }

    @Test
    fun scaledCompoundHull_ScalesEveryHull() {
        val go = body(CompoundHullCollider3D(listOf(cube(0.5f), cube(0.5f, Vector3f(2f, 0f, 0f)))))
        go.getComponent<Transform>()!!.scale.set(2f)
        world.add(go)

        // Second hull centre moves to x=4, its top to y=1
        val hit = world.raycast(Vector3f(4.8f, 5f, 0f), Vector3f(4.8f, -5f, 0f))
        assertEquals(1f, hit!!.point.y, 0.05f)
    }

    @Test
    fun raycast_BeforeAnyStep_SeesBodiesAtTheirAddedAndTeleportedPose() {
        val go = body(CompoundHullCollider3D(listOf(cube(1f))), at = Vector3f(30f, 0f, 0f))
        world.add(go)
        assertEquals(go, world.raycast(Vector3f(30f, 5f, 0f), Vector3f(30f, -5f, 0f))?.gameObject)

        go.getComponent<Transform>()!!.translation.set(-20f, 0f, 0f)
        world.teleport(go)
        assertEquals(go, world.raycast(Vector3f(-20f, 5f, 0f), Vector3f(-20f, -5f, 0f))?.gameObject)
        assertEquals(null, world.raycast(Vector3f(30f, 5f, 0f), Vector3f(30f, -5f, 0f)))
    }

    // --- same collision as one CustomCollider3D per hull ---

    @Test
    fun bowlAndHalfPipe_CompoundHull_CollidesLikeOneCustomColliderPerHull() {
        listOf(PrefabType.BOWL, PrefabType.HALF_PIPE).forEach { type ->
            val hulls = PrefabCatalog.shape(type)!!.hulls
            val reference = BulletPhysics3D(debug)
            reference.add(body(*hulls.map { CustomCollider3D(it) }.toTypedArray()))
            world.add(body(CompoundHullCollider3D(hulls)))

            assertSameHeights(reference, world, hulls.flatten(), type)
            reference.destroy()
            world.destroy()
            world = BulletPhysics3D(debug)
        }
    }

    // --- debug draw ---

    @Test
    fun debugDraw_HullAndMesh_DrawFeatureEdges() {
        world.add(body(CustomCollider3D(cube(1f))))
        world.debugEnabled = true

        world.debugDraw(0f)

        verify(exactly = 12) { debug.addLine3D(any(), any(), any(), any()) }
    }

    @Test
    fun debugDraw_TriangleMeshQuad_DrawsItsOutlineOnly() {
        world.add(body(MeshCollider3D()))
        world.debugEnabled = true

        world.debugDraw(0f)

        verify(exactly = 4) { debug.addLine3D(any(), any(), any(), any()) }
    }

    companion object {
        /** Casts a grid of vertical rays over [points]' footprint in both worlds and compares hit heights. */
        fun assertSameHeights(expected: IPhysics3D, actual: IPhysics3D, points: List<Vector3f>, what: Any) {
            val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
            val minZ = points.minOf { it.z }; val maxZ = points.maxOf { it.z }
            val top = points.maxOf { it.y } + 5f
            var hits = 0
            for (i in 0..12) for (j in 0..12) {
                // Inset from the footprint edges, where a ray grazing a hull face can go either way
                val x = minX + (maxX - minX) * (0.01f + 0.98f * i / 12f)
                val z = minZ + (maxZ - minZ) * (0.01f + 0.98f * j / 12f)
                val from = Vector3f(x, top, z)
                val to = Vector3f(x, -5f, z)
                val e = expected.raycast(from, to)?.point?.y
                val a = actual.raycast(from, to)?.point?.y
                if (e == null) assertEquals(null, a, "$what at ($x, $z)") else {
                    hits++
                    assertEquals(e, a ?: Float.NaN, 1e-3f, "$what at ($x, $z)")
                }
            }
            assertTrue(hits > 50, "$what: only $hits rays hit")
        }
    }
}
