package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** PHY-22: the scene query interface (`docs/design/GPL-24.md` §4.3), the same tests on every backend. */
class ScenePhysicsQueriesTest {
    companion object {
        @JvmStatic
        fun backends(): List<PhysicsBackend> = PhysicsBackends.core() + PhysicsBackend.SIMPLE

        /** A sphere for every backend (none has a sphere shape). */
        fun sphere(radius: Float) = ColliderShape.Capsule(radius, 1e-3f, 1)
    }

    private lateinit var physics: IPhysics3D
    private lateinit var queries: ScenePhysicsQueries

    private fun use(backend: PhysicsBackend) {
        physics = PhysicsBackends.create(backend)
        physics.setGravity(Vector3f())
        queries = ScenePhysicsQueries.of(physics)
    }

    @AfterEach
    fun tearDown() {
        if (::physics.isInitialized) physics.destroy()
    }

    private fun box(
        name: String,
        at: Vector3f,
        half: Float = 0.5f,
        mass: Float = 0f,
        configure: RigidBody3D.() -> Unit = {},
    ) = GameObject(name).apply {
        addComponent(Transform(Vector3f(at)))
        addComponent(RigidBody3D(mass).apply(configure))
        addComponent(BoxCollider3D(Vector3f(half)))
    }.also { physics.add(it) }

    private fun settle() = physics.step(1f / 60f)

    private fun names(list: List<GameObject>) = list.map { it.name }

    @ParameterizedTest
    @MethodSource("backends")
    fun sphereOverlap_FindsStaticAndDynamicBodiesInside_NearestFirst(backend: PhysicsBackend) {
        use(backend)
        box("Far side", Vector3f(-2.3f, 0f, 0f))
        box("Near", Vector3f(1f, 0f, 0f))
        box("Floating", Vector3f(0f, 1.6f, 0f), half = 0.25f, mass = 1f)
        box("Outside", Vector3f(0f, 0f, 3f))
        settle()

        assertEquals(listOf("Near", "Floating", "Far side"), names(queries.overlap(sphere(2f), Vector3f())), backend.name)
        assertTrue(queries.overlap(sphere(0.3f), Vector3f(0f, -5f, 0f)).isEmpty(), "$backend: empty space")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun boxOverlap_FollowsTheQueryRotation(backend: PhysicsBackend) {
        use(backend)
        box("Along Z", Vector3f(0f, 0f, 1.5f), half = 0.25f, mass = 1f)
        box("Along X", Vector3f(1.5f, 0f, 0f), half = 0.25f, mass = 1f)
        settle()

        val beam = ColliderShape.Box(Vector3f(2f, 0.2f, 0.2f))
        assertEquals(listOf("Along X"), names(queries.overlap(beam, Vector3f())), "$backend: unrotated beam lies along X")
        val turned = Quaternionf().rotationY(Math.toRadians(90.0).toFloat())
        assertEquals(listOf("Along Z"), names(queries.overlap(beam, Vector3f(), turned)), "$backend: turned beam lies along Z")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun overlap_SkipsOtherLayers_Triggers_TheExcludedBody_AndBodiesWithoutAnObject(backend: PhysicsBackend) {
        use(backend)
        val self = box("Self", Vector3f(0f, 0f, 0f), half = 0.25f, mass = 1f)
        box("Layer 2", Vector3f(0.8f, 0f, 0f), half = 0.25f, mass = 1f) { collisionLayer = 2 }
        box("Trigger", Vector3f(-0.8f, 0f, 0f), half = 0.25f) { isTrigger = true }
        box("Target", Vector3f(0f, 0f, 0.9f), half = 0.25f, mass = 1f)
        physics.createRigidBody(ColliderShape.Box(Vector3f(0.25f)), 0f).setLocation(Vector3f(0f, 0f, -0.9f))
        settle()

        val selfBody = self.getComponent<RigidBody3D>()!!
        assertEquals(listOf("Target"), names(queries.overlap(sphere(1.2f), Vector3f(), layerMask = 1, excludeBody = selfBody)), backend.name)
        // Without filters: still no trigger and no object-less body; ordered by distance (0, 0.8, 0.9)
        assertEquals(listOf("Self", "Layer 2", "Target"), names(queries.overlap(sphere(1.2f), Vector3f())), backend.name)
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun overlap_RejectsMeshAndHeightfieldQueries(backend: PhysicsBackend) {
        use(backend)
        val mesh = ColliderShape.TriangleMesh(listOf(Vector3f(), Vector3f(1f, 0f, 0f), Vector3f(0f, 0f, 1f)), listOf(0, 1, 2))
        assertThrows(IllegalArgumentException::class.java) { queries.overlap(mesh, Vector3f()) }
        assertThrows(IllegalArgumentException::class.java) { queries.overlap(ColliderShape.Heightfield(2, 1f, FloatArray(4)), Vector3f()) }
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun raysAndSweeps_ThroughTheInterface_MatchTheWorld(backend: PhysicsBackend) {
        use(backend)
        val wall = box("Wall", Vector3f(5f, 0f, 0f))
        val behind = box("Behind", Vector3f(8f, 0f, 0f))
        settle()

        val ray = queries.raycast(Vector3f(), Vector3f(10f, 0f, 0f))!!
        assertSame(wall, ray.gameObject)
        assertEquals(4.5f, ray.point.x, 0.05f, backend.name)
        assertEquals(listOf(wall, behind), queries.raycastAll(Vector3f(), Vector3f(10f, 0f, 0f)).map { it.gameObject })
        val sweep = queries.shapeCast(sphere(0.5f), Vector3f(), Vector3f(10f, 0f, 0f))!!
        assertSame(wall, sweep.gameObject)
        assertEquals(4f, sweep.point.x, 0.1f, "$backend: sphere centre at impact")
    }

    @Test
    fun physicsSystem_ExposesTheQueriesOfItsWorld() {
        NativeLibraryLoader().loadNativeLibrary()
        physics = PhysicsBackends.create(PhysicsBackend.BULLET)
        val system = PhysicsSystem(mockk<DebugRenderer>(relaxed = true), physics3d = physics)
        val target = box("Target", Vector3f(0f, 0f, 1f))
        settle()

        assertEquals(listOf(target), system.queries.overlap(sphere(1f), Vector3f()))
        assertSame(target, system.queries.raycast(Vector3f(0f, 0f, -5f), Vector3f(0f, 0f, 5f))?.gameObject)
    }
}
