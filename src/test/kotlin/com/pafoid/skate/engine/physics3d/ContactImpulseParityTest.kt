package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.sqrt

/** PHY-29a: `Collision.impulse` is the same quantity on every backend (`docs/design/PHY-29.md` §5.2). */
class ContactImpulseParityTest {
    companion object {
        @JvmStatic
        fun backends(): List<PhysicsBackend> = PhysicsBackends.core() + PhysicsBackend.SIMPLE
    }

    private lateinit var physics: IPhysics3D
    private val dt = 1f / 60f
    private val impulses = mutableListOf<Triple<GameObject, GameObject, Float>>()

    private fun use(backend: PhysicsBackend, gravity: Vector3f = Vector3f(0f, -9.81f, 0f)) {
        physics = PhysicsBackends.create(backend)
        physics.setGravity(gravity)
        physics.contactListener = object : PhysicsContactListener {
            override fun onCollisionStarted(a: GameObject, b: GameObject, point: Vector3f, normal: Vector3f, impulse: Float) {
                impulses += Triple(a, b, impulse)
            }
            override fun onTriggerEnter(trigger: GameObject, other: GameObject) {}
            override fun onTriggerExit(trigger: GameObject, other: GameObject) {}
        }
    }

    @AfterEach
    fun tearDown() {
        if (::physics.isInitialized) physics.destroy()
    }

    private fun box(name: String, at: Vector3f, half: Vector3f, mass: Float) = GameObject(name).apply {
        addComponent(Transform(Vector3f(at)))
        addComponent(RigidBody3D(mass))
        addComponent(BoxCollider3D(Vector3f(half)))
    }.also { physics.add(it) }

    @Test
    fun contactImpulse_IsReducedMassTimesClosingSpeed() {
        assertEquals(30f, ContactImpulse.of(-6f, 0.1f, 0.1f), 1e-4f, "two 10 kg bodies closing at 6 m/s")
        assertEquals(60f, ContactImpulse.of(-6f, 0.1f, 0f), 1e-4f, "10 kg against static ground")
        assertEquals(0f, ContactImpulse.of(2f, 0.1f, 0.1f), "separating")
        assertEquals(0f, ContactImpulse.of(-2f, 0f, 0f), "two immovable bodies")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun droppedBox_ReportsMassTimesImpactSpeed(backend: PhysicsBackend) {
        use(backend)
        val floor = box("Floor", Vector3f(0f, -0.5f, 0f), Vector3f(10f, 0.5f, 10f), 0f)
        // The Simple backend treats a moving box as its bounding sphere: drop a flat box from the same clearance
        val crate = box("Crate", Vector3f(0f, 3f, 0f), Vector3f(0.5f), 10f)
        val lowest = if (backend == PhysicsBackend.SIMPLE) Vector3f(0.5f).length() else 0.5f
        val fall = 3f - lowest

        repeat(120) { physics.step(dt) }

        val hit = impulses.firstOrNull { (a, b, _) -> a === crate && b === floor || a === floor && b === crate }
        assertTrue(hit != null, "$backend: the crate hit the floor")
        val expected = 10f * sqrt(2f * 9.81f * fall)
        assertEquals(expected, hit!!.third, expected * 0.1f, "$backend: impulse of a ${"%.2f".format(fall)} m fall")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun headOnBodies_ReportReducedMassTimesClosingSpeed(backend: PhysicsBackend) {
        use(backend, gravity = Vector3f())
        val left = box("Left", Vector3f(-2f, 0f, 0f), Vector3f(0.5f), 10f)
        val right = box("Right", Vector3f(2f, 0f, 0f), Vector3f(0.5f), 10f)
        left.getComponent<RigidBody3D>()!!.linearVelocity = Vector3f(3f, 0f, 0f)
        right.getComponent<RigidBody3D>()!!.linearVelocity = Vector3f(-3f, 0f, 0f)

        repeat(60) { physics.step(dt) }

        val hit = impulses.firstOrNull { (a, b, _) -> setOf(a, b) == setOf(left, right) }
        assertTrue(hit != null, "$backend: the boxes met")
        assertEquals(30f, hit!!.third, 3f, "$backend: (10 × 10 / 20) kg × 6 m/s")
    }
}
