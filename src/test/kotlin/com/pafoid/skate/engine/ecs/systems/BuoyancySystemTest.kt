package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterPreset
import com.pafoid.skate.engine.environment.GerstnerWave
import com.pafoid.skate.engine.environment.WaterQueries
import com.pafoid.skate.engine.events.WaterEntered
import com.pafoid.skate.engine.events.WaterExited
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.physics3d.PhysicsBackends
import com.pafoid.skate.engine.physics3d.water.BuoyancyMode
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.abs

/** PHY-16: bodies float, settle, right themselves and drift in water on every backend (`docs/design/REN-29.md` §8). */
class BuoyancySystemTest {
    companion object {
        @JvmStatic
        fun backends(): List<PhysicsBackend> = PhysicsBackends.core() + PhysicsBackend.SIMPLE

        /** SimplePhysics3D has no rotation dynamics. */
        @JvmStatic
        fun rotatingBackends(): List<PhysicsBackend> = PhysicsBackends.core()
    }

    private lateinit var physics: IPhysics3D
    private val events = EventSystem()
    private val buoyancy = BuoyancySystem(events)
    private val queries = WaterQueries()
    private val gravity = Vector3f(0f, -9.81f, 0f)
    private val dt = 1f / 60f
    private var time = 0f
    private val entered = mutableListOf<WaterEntered>()
    private val exited = mutableListOf<WaterExited>()
    private val bodies = mutableListOf<GameObject>()
    private val waters = mutableListOf<GameObject>()

    private fun use(backend: PhysicsBackend) {
        physics = PhysicsBackends.create(backend)
        physics.setGravity(gravity)
        events.subscribe<WaterEntered> { entered += it }
        events.subscribe<WaterExited> { exited += it }
    }

    @AfterEach
    fun tearDown() {
        if (::physics.isInitialized) physics.destroy()
    }

    /** A 10 × 10 m pool, 3 m deep, surface at y = 0 (flat unless [waves] are given). */
    private fun pool(
        waves: MutableList<GerstnerWave> = mutableListOf(),
        at: Vector3f = Vector3f(),
        configure: WaterComponent.() -> Unit = {},
    ) = GameObject("Pool").apply {
        addComponent(Transform(Vector3f(at)).also { it.worldMatrix.translation(at) })
        addComponent(WaterComponent(sizeX = 10f, sizeZ = 10f, depth = 3f, waves = waves).apply(configure))
    }.also { waters += it }

    private fun box(half: Vector3f, mass: Float, at: Vector3f, rotation: Vector3f = Vector3f()) = GameObject("Crate").apply {
        addComponent(Transform(Vector3f(at)).apply { this.rotation.set(rotation) })
        addComponent(RigidBody3D(mass))
        addComponent(BoxCollider3D(Vector3f(half)))
    }.also { physics.add(it); bodies += it }

    private fun run(seconds: Float, each: () -> Unit = {}) {
        repeat((seconds / dt).toInt()) {
            time += dt
            queries.refresh(waters, null)
            buoyancy.step(bodies, queries, time, dt, gravity)
            physics.step(dt)
            each()
        }
    }

    private fun GameObject.handle() = getComponent<RigidBody3D>()!!.rawBody!!
    private fun GameObject.y() = handle().getLocation(Vector3f()).y

    @ParameterizedTest
    @MethodSource("backends")
    fun halfDensityCube_SettlesAtHalfDepth(backend: PhysicsBackend) {
        use(backend)
        pool()
        val crate = box(Vector3f(0.5f), 500f, Vector3f(0f, 1f, 0f))

        run(10f)

        assertEquals(0f, crate.y(), 0.06f, "$backend: centre at the surface")
        assertTrue(crate.handle().linearVelocity.length() < 0.1f, "$backend: settled, v=${crate.handle().linearVelocity}")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun densities_FloatHigherLowerOrSink(backend: PhysicsBackend) {
        use(backend)
        pool()
        val light = box(Vector3f(0.5f), 250f, Vector3f(-3f, 1f, 0f))
        val heavy = box(Vector3f(0.5f), 750f, Vector3f(0f, 1f, 0f))
        val dense = box(Vector3f(0.5f), 3000f, Vector3f(3f, 1f, 0f))

        run(10f)

        assertEquals(0.25f, light.y(), 0.07f, "$backend: a quarter under")
        assertEquals(-0.25f, heavy.y(), 0.07f, "$backend: three quarters under")
        assertTrue(dense.y() < -3f, "$backend: denser than water sinks, y=${dense.y()}")
    }

    /** The Showcase pool's other props (REN-29 §12): a ball and a log, both lighter than water. */
    @ParameterizedTest
    @MethodSource("backends")
    fun ballAndLog_FloatAtTheSurfaceAndSettle(backend: PhysicsBackend) {
        use(backend)
        pool()
        // Ball: radius 0.3 m at 300 kg/m³ (a "sphere" capsule); log: radius 0.25 m, 2 m long along X, at 600 kg/m³
        val ball = GameObject("Ball").apply {
            addComponent(Transform(Vector3f(-2f, 1f, 0f)))
            addComponent(RigidBody3D(300f * 4.18879f * 0.027f))
            addComponent(CapsuleCollider3D(0.3f, 1e-3f))
        }.also { physics.add(it); bodies += it }
        val log = GameObject("Log").apply {
            addComponent(Transform(Vector3f(2f, 1f, 0f)))
            addComponent(RigidBody3D(600f * 3.14159f * 0.0625f * 2f))
            addComponent(CylinderCollider3D(0.25f, 2f, axis = 0))
        }.also { physics.add(it); bodies += it }

        run(10f)

        for (body in listOf(ball, log)) {
            val y = body.y()
            assertTrue(y > -0.25f && y < 0.25f, "$backend: ${body.name} floats at the surface, y=$y")
            assertTrue(body.handle().linearVelocity.length() < 0.1f, "$backend: ${body.name} settled")
        }
        assertTrue(ball.y() > log.y(), "$backend: the lighter ball rides higher")
    }

    @ParameterizedTest
    @MethodSource("rotatingBackends")
    fun tiltedPlank_RightsItselfAndSettlesFlat(backend: PhysicsBackend) {
        use(backend)
        pool()
        val plank = box(Vector3f(1f, 0.2f, 1f), 800f, Vector3f(0f, 0.5f, 0f), rotation = Vector3f(0f, 0f, 25f))

        run(8f)

        val up = plank.handle().getRotation(Quaternionf()).transform(Vector3f(0f, 1f, 0f))
        assertTrue(up.angle(Vector3f(0f, 1f, 0f)) < Math.toRadians(5.0), "$backend: still tilted, up=$up")
        assertEquals(0f, plank.y(), 0.06f, "$backend: half depth")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun riverFlow_CarriesFloatingBodiesDownstream(backend: PhysicsBackend) {
        use(backend)
        // Flow authored along the water's +X, the water turned 90° (yaw): +X local is −Z world
        pool(at = Vector3f()) { flowVelocity.set(1f, 0f); sizeX = 40f; sizeZ = 40f }.also {
            it.getComponent<Transform>()!!.worldMatrix.rotationY(Math.toRadians(90.0).toFloat())
        }
        val crate = box(Vector3f(0.5f), 500f, Vector3f(0f, 0f, 0f))

        run(6f)

        val at = crate.handle().getLocation(Vector3f())
        val v = crate.handle().linearVelocity
        assertTrue(at.z < -2f && abs(at.x) < 0.5f, "$backend: carried along −Z, at $at")
        assertEquals(-1f, v.z, 0.2f, "$backend: moves with the flow, v=$v")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun waves_MakeFloatingBodiesBob(backend: PhysicsBackend) {
        use(backend)
        pool(WaterPreset.LAKE.waves())
        val crate = box(Vector3f(0.5f), 500f, Vector3f(1f, 0f, 1f))
        run(5f)

        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        run(5f) {
            min = minOf(min, crate.y())
            max = maxOf(max, crate.y())
        }
        assertTrue(max - min > 0.01f, "$backend: bobs, range ${max - min}")
        assertTrue(min > -0.3f && max < 0.3f, "$backend: stays near the surface, $min..$max")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun waterEvents_AreSentOncePerEntryAndExit(backend: PhysicsBackend) {
        use(backend)
        val water = pool()
        // Lowered in gently: a crate dropped from high can pop out and splash in again (two entries)
        val crate = box(Vector3f(0.5f), 500f, Vector3f(0f, 0.6f, 0f))

        run(3f)
        assertEquals(listOf(WaterEntered(water, crate)), entered)
        assertTrue(exited.isEmpty())

        crate.handle().setLocation(Vector3f(30f, 0f, 0f))
        run(0.1f)
        assertEquals(listOf(WaterExited(water, crate)), exited)
        assertEquals(0, buoyancy.bodiesInWater)
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun controllerBodies_GetEventsButNoForce_AndOffBodiesGetNeither(backend: PhysicsBackend) {
        use(backend)
        val water = pool()
        val swimmer = GameObject("Swimmer").apply {
            addComponent(Transform(Vector3f(-2f, 1f, 0f)))
            addComponent(RigidBody3D(40f).apply { buoyancy = BuoyancyMode.CONTROLLER })
            addComponent(CapsuleCollider3D(0.3f, 1.2f))
        }.also { physics.add(it); bodies += it }
        val ignored = box(Vector3f(0.5f), 100f, Vector3f(2f, 1f, 0f)).also {
            it.getComponent<RigidBody3D>()!!.buoyancy = BuoyancyMode.OFF
        }

        run(2f)

        assertEquals(listOf(WaterEntered(water, swimmer)), entered)
        assertTrue(swimmer.y() < -3f, "${backend}: no buoyancy on a controller body, y=${swimmer.y()}")
        assertTrue(ignored.y() < -3f, "${backend}: OFF body sinks, y=${ignored.y()}")
    }

    @ParameterizedTest
    @MethodSource("backends")
    fun removedWater_SendsExitForBodiesInIt(backend: PhysicsBackend) {
        use(backend)
        val water = pool()
        val crate = box(Vector3f(0.5f), 500f, Vector3f(0f, 0f, 0f))
        run(0.5f)
        assertEquals(1, entered.size)

        waters.clear()
        run(dt)
        assertEquals(listOf(WaterExited(water, crate)), exited)
    }
}
