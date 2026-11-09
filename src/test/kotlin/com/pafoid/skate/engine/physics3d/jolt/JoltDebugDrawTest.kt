package com.pafoid.skate.engine.physics3d.jolt

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.CustomCollider3D
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.physics3d.PhysicsBackends
import com.pafoid.skate.engine.physics3d.ShapePart
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.every
import io.mockk.mockk
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

/** PHY-17e: Jolt collider wireframes. */
class JoltDebugDrawTest {
    private val renderer = mockk<DebugRenderer>(relaxed = true)
    private val lines = mutableListOf<Pair<Vector3f, Vector3f>>()
    /** Centre and half extents of each box drawn (copied: the drawer reuses its vectors). */
    private val boxes = mutableListOf<Pair<Vector3f, Vector3f>>()
    private val cylinders = mutableListOf<List<Any>>()
    private lateinit var current: IPhysics3D

    init {
        every { renderer.addLine3D(any(), any(), any(), any()) } answers { lines += Vector3f(firstArg<Vector3f>()) to Vector3f(secondArg<Vector3f>()) }
        every { renderer.addBox3D(any(), any(), any(), any()) } answers { boxes += Vector3f(firstArg<Vector3f>()) to Vector3f(thirdArg<Vector3f>()) }
        every { renderer.addCylinder3D(any(), any(), any(), any(), any(), any()) } answers {
            cylinders += listOf(Vector3f(firstArg<Vector3f>()), arg<Float>(2), arg<Float>(3), arg<Int>(4))
        }
    }

    private fun world() = PhysicsBackends.create(PhysicsBackend.JOLT, renderer).also { current = it; it.debugEnabled = true }

    @AfterEach
    fun tearDown() {
        if (::current.isInitialized) current.destroy()
    }

    private fun IPhysics3D.body(at: Vector3f, vararg colliders: Component, scale: Float = 1f) = GameObject("Body").apply {
        addComponent(Transform(Vector3f(at)).apply { this.scale.set(scale) })
        addComponent(RigidBody3D(0f))
        colliders.forEach { attachComponent(it) }
        add(this)
    }

    @Test
    fun JoltDebugDraw_Disabled_DrawsNothing() {
        val w = world()
        w.body(Vector3f(), BoxCollider3D())
        w.debugEnabled = false
        w.debugDraw(0f)
        assertTrue(boxes.isEmpty())
    }

    @Test
    fun JoltDebugDraw_OffsetBoxes_DrawAtTheirScaledCentres() {
        val w = world()
        w.body(
            Vector3f(0f, 1f, 0f),
            BoxCollider3D(Vector3f(0.5f), offset = Vector3f(-1f, 0f, 0f)),
            BoxCollider3D(Vector3f(0.5f), offset = Vector3f(1f, 0f, 0f)),
            scale = 2f,
        )
        w.debugDraw(0f)
        assertEquals(listOf(Vector3f(-2f, 1f, 0f) to Vector3f(1f), Vector3f(2f, 1f, 0f) to Vector3f(1f)), boxes)
    }

    @Test
    fun JoltDebugDraw_CapsuleAndCylinder_UseTheCylinderPrimitive() {
        val w = world()
        w.body(Vector3f(), CapsuleCollider3D(radius = 0.25f, height = 1f, axis = 0))
        w.body(Vector3f(5f, 0f, 0f), CylinderCollider3D(radius = 0.5f, height = 2f, axis = 2))
        w.debugDraw(0f)
        // A capsule is drawn as a cylinder spanning its caps, like the Bullet backend
        assertEquals(
            listOf(listOf(Vector3f(), 0.25f, 1.5f, 0), listOf(Vector3f(5f, 0f, 0f), 0.5f, 2f, 2)),
            cylinders,
        )
    }

    @Test
    fun JoltDebugDraw_HullCube_DrawsItsTwelveEdges() {
        val w = world()
        w.body(Vector3f(0f, 3f, 0f), CustomCollider3D(cube(0.5f)))
        w.debugDraw(0f)
        assertEquals(12, lines.size)
        assertTrue(lines.all { (a, b) -> a.y in 2.49f..3.51f && b.y in 2.49f..3.51f })

        lines.clear()
        w.debugDraw(0f) // cached edges, same result
        assertEquals(12, lines.size)
    }

    @Test
    fun JoltDebugDraw_Mesh_DrawsItsOutlineOnly() {
        val w = world()
        w.body(Vector3f(), MeshCollider3D()) // a 2 m quad made of two triangles
        w.debugDraw(0f)
        assertEquals(4, lines.size)
    }

    @Test
    fun JoltDebugDraw_LargeHull_UsesJoltsMeshAlignedToThePoints() {
        // 100 points on a sphere of radius 1 around (3, 0, 0): too many for the brute-force hull outline
        val points = (0 until 100).map { i ->
            val t = i * 2.399963f
            val y = 1f - 2f * (i + 0.5f) / 100f
            val r = kotlin.math.sqrt(1f - y * y)
            Vector3f(3f + r * cos(t), y, r * sin(t))
        }
        val edges = JoltDebugDraw.localEdges(ShapePart(ColliderShape.ConvexHull(points), Vector3f()), Vector3f(1f))
        assertTrue(edges.isNotEmpty())
        val xs = edges.filterIndexed { i, _ -> i % 3 == 0 }
        assertEquals(2f, xs.min(), 0.05f)
        assertEquals(4f, xs.max(), 0.05f)
    }

    @Test
    fun JoltDebugDraw_FollowsTheBodyRotation() {
        val w = world()
        val go = w.body(Vector3f(), BoxCollider3D(Vector3f(0.5f), offset = Vector3f(1f, 0f, 0f)), BoxCollider3D())
        go.components.filterIsInstance<RigidBody3D>().first().rawBody!!.setRotation(Quaternionf().rotationY(Math.PI.toFloat()))
        w.debugDraw(0f)
        assertTrue(boxes.any { it.first.distance(-1f, 0f, 0f) < 1e-4f }, "boxes $boxes")
    }

    private fun cube(half: Float) = listOf(-1f, 1f).flatMap { x ->
        listOf(-1f, 1f).flatMap { y -> listOf(-1f, 1f).map { z -> Vector3f(x, y, z).mul(half) } }
    }
}
