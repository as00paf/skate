package com.pafoid.skate.engine.physics3d.jolt

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.physics3d.PhysicsBackends
import com.pafoid.skate.engine.physics3d.PhysicsContactListener
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import com.sun.management.OperatingSystemMXBean
import io.mockk.mockk
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory

/** PHY-17e: Jolt worlds free their native resources, and single-threaded simulation is deterministic. */
class JoltLifecycleTest {

    private fun IPhysics3D.body(at: Vector3f, collider: Component, mass: Float = 1f) = GameObject("Body").apply {
        addComponent(Transform(Vector3f(at)))
        addComponent(RigidBody3D(mass))
        attachComponent(collider)
        add(this)
    }

    /** A small busy scene: ground, a pile of boxes and capsules, a jointed pair, a contact listener. */
    private fun populate(w: IPhysics3D): List<GameObject> {
        w.contactListener = object : PhysicsContactListener {
            override fun onCollisionStarted(a: GameObject, b: GameObject, point: Vector3f, normal: Vector3f, impulse: Float) {}
            override fun onTriggerEnter(trigger: GameObject, other: GameObject) {}
            override fun onTriggerExit(trigger: GameObject, other: GameObject) {}
        }
        val bodies = mutableListOf(w.body(Vector3f(0f, -0.5f, 0f), BoxCollider3D(Vector3f(20f, 0.5f, 20f)), mass = 0f))
        for (i in 0 until 4) for (j in 0 until 4) for (k in 0 until 4) {
            // Small offsets so the pile topples in a non-trivial way
            val at = Vector3f(i * 1.1f + k * 0.07f, 0.5f + j * 1.05f, (i + j) * 0.03f + k * 1.1f)
            bodies += w.body(at, if ((i + j + k) % 3 == 0) CapsuleCollider3D(0.3f, 0.4f) else BoxCollider3D(Vector3f(0.5f)))
        }
        val a = w.createRigidBody(ColliderShape.Box(Vector3f(0.2f)), 1f).also { it.setLocation(Vector3f(-5f, 3f, 0f)) }
        val b = w.createRigidBody(ColliderShape.Box(Vector3f(0.2f)), 1f).also { it.setLocation(Vector3f(-5f, 2.5f, 0f)) }
        w.addConstraint(HingeConstraint3D(a, b, Vector3f(0f, -0.25f, 0f), Vector3f(0f, 0.25f, 0f), Vector3f(1f, 0f, 0f), Vector3f(1f, 0f, 0f)))
        return bodies
    }

    private fun memSnapshot(os: OperatingSystemMXBean, tag: String): String {
        val mm = ManagementFactory.getMemoryMXBean()
        val pools = ManagementFactory.getMemoryPoolMXBeans()
        val detail = pools.joinToString(" ") { "${it.name.replace(" ", "")}=${it.usage.committed / (1024 * 1024)}" }
        val threads = ManagementFactory.getThreadMXBean()
        return "MEM[$tag] total=${os.committedVirtualMemorySize / (1024 * 1024)}MB " +
            "heapC=${mm.heapMemoryUsage.committed / (1024 * 1024)}MB heapU=${mm.heapMemoryUsage.used / (1024 * 1024)}MB " +
            "nonHeapC=${mm.nonHeapMemoryUsage.committed / (1024 * 1024)}MB threads=${threads.threadCount} :: $detail"
    }

    @Test
    fun JoltLifecycle_FiftyWorlds_FreeEverythingTheyOwn() {
        val os = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        val threads = ManagementFactory.getThreadMXBean()
        var memoryAfterWarmup = 0L
        var threadsAfterWarmup = 0

        println(memSnapshot(os, "start"))
        repeat(50) { n ->
            val w = PhysicsBackends.create(PhysicsBackend.JOLT) as JoltPhysics3D
            populate(w)
            assertEquals(67, w.rigidBodyCount)
            repeat(30) { w.step(1f / 60f) }
            val owned = w.ownedNativeObjects()

            w.destroy()

            val leaked = owned.filter { it.hasAssignedNativeObject() }
            assertTrue(leaked.isEmpty(), "world $n kept ${leaked.map { it::class.simpleName }}")
            if (n == 9) {
                System.gc()
                memoryAfterWarmup = os.committedVirtualMemorySize
                threadsAfterWarmup = threads.threadCount
                println(memSnapshot(os, "baseline-n9"))
            }
            if (n % 10 == 4) println(memSnapshot(os, "n${n + 1}-preGC"))
        }
        System.gc()
        println(memSnapshot(os, "end-postGC"))
        // Each world holds a 16 MB temp allocator and a thread pool: leaking them would grow by ~640 MB and 100+ threads
        val growthMb = (os.committedVirtualMemorySize - memoryAfterWarmup) / (1024 * 1024)
        println("DIAG growth=$growthMb MB totalStart=${os.committedVirtualMemorySize / (1024 * 1024)}MB")
        assertTrue(growthMb < 128, "committed memory grew by $growthMb MB over 40 worlds")
        assertTrue(threads.threadCount - threadsAfterWarmup < 8, "threads grew from $threadsAfterWarmup to ${threads.threadCount}")
    }

    @Test
    fun JoltDeterminism_SingleThread_SameSceneGivesIdenticalPoses() {
        fun run(): List<Pair<Vector3f, Quaternionf>> {
            val w = JoltPhysics3D(mockk(relaxed = true), threads = 1)
            try {
                val bodies = populate(w)
                repeat(300) { w.step(1f / 60f) }
                return bodies.map { go ->
                    val handle = go.components.filterIsInstance<RigidBody3D>().first().rawBody!!
                    handle.getLocation(Vector3f()) to handle.getRotation(Quaternionf())
                }
            } finally {
                w.destroy()
            }
        }
        JoltRuntime.ensureLoaded()

        val first = run()
        val second = run()

        assertTrue(first.drop(1).any { it.first.y < 0.6f && it.first.y > 0.4f }, "the pile should have settled")
        assertEquals(first, second)
    }
}
