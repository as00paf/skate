package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.VehicleComponent
import com.pafoid.skate.engine.render.EngineStats
import org.joml.Vector3f
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * PHY-17e manual benchmark, not part of CI: Bullet vs Jolt `physicsStepTime` on three load scenes. Run with
 * `cmd.exe /c "set SKATE_BENCHMARK=1&& gradlew.bat :test --tests com.pafoid.skate.engine.physics3d.PhysicsBenchmarkTest --rerun"`.
 * Results go to stdout and build/reports/physics-benchmark.md.
 */
class PhysicsBenchmarkTest {
    private val dt = 1f / 60f
    private val warmup = 60
    private val measured = 600

    private class Result(val scene: String, val backend: PhysicsBackend, val stepsNs: LongArray, val active: Int, val bodies: Int) {
        private val sorted = stepsNs.sortedArray()
        fun ms(ns: Double) = "%.2f".format(ns / 1e6)
        val mean get() = ms(stepsNs.average())
        val p50 get() = ms(sorted[sorted.size / 2].toDouble())
        val p95 get() = ms(sorted[(sorted.size * 95) / 100].toDouble())
        val max get() = ms(sorted.last().toDouble())
    }

    @Test
    fun benchmark_BulletVsJolt() {
        assumeTrue(System.getenv("SKATE_BENCHMARK") == "1", "manual benchmark: set SKATE_BENCHMARK=1")
        val results = mutableListOf<Result>()
        for (backend in listOf(PhysicsBackend.BULLET, PhysicsBackend.JOLT)) {
            results += run("1000-box pile", backend) { w -> boxPile(w); null }
            results += run("50 ragdolls", backend) { w -> ragdolls(w); null }
            results += run("20 vehicles", backend) { w -> vehicles(w) }
        }
        val cores = Runtime.getRuntime().availableProcessors()
        val report = buildString {
            appendLine("Steps: $warmup warm-up + $measured measured at 60 Hz; cores: $cores; Jolt solver threads: ${(cores - 1).coerceIn(1, 4)}")
            appendLine()
            appendLine("| Scene | Backend | Mean ms | p50 ms | p95 ms | Max ms | Active / bodies at end |")
            appendLine("|---|---|---|---|---|---|---|")
            results.forEach { appendLine("| ${it.scene} | ${it.backend} | ${it.mean} | ${it.p50} | ${it.p95} | ${it.max} | ${it.active} / ${it.bodies} |") }
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/physics-benchmark.md").writeText(report)
    }

    /** [setup] may return a per-step action run before each physics step (e.g. vehicle solvers); its time is excluded. */
    private fun run(scene: String, backend: PhysicsBackend, setup: (IPhysics3D) -> (() -> Unit)?): Result {
        val w = PhysicsBackends.create(backend)
        try {
            val beforeStep = setup(w)
            repeat(warmup) { beforeStep?.invoke(); w.step(dt) }
            val samples = LongArray(measured) {
                beforeStep?.invoke()
                w.step(dt)
                EngineStats.physicsStepTime.get()
            }
            return Result(scene, backend, samples, w.activeBodyCount, w.rigidBodyCount)
        } finally {
            w.destroy()
        }
    }

    private fun ground(w: IPhysics3D) = GameObject("Ground").apply {
        addComponent(Transform(Vector3f(0f, -0.5f, 0f)))
        addComponent(RigidBody3D(0f, BodyType.Static))
        addComponent(BoxCollider3D(Vector3f(300f, 0.5f, 300f)))
        w.add(this)
    }

    private fun boxPile(w: IPhysics3D) {
        ground(w)
        for (i in 0 until 10) for (j in 0 until 10) for (k in 0 until 10) {
            GameObject("Box").apply {
                addComponent(Transform(Vector3f(i * 1.05f + j * 0.02f, 0.5f + j * 1.02f, k * 1.05f)))
                addComponent(RigidBody3D(1f))
                addComponent(BoxCollider3D(Vector3f(0.5f)))
                w.add(this)
            }
        }
    }

    private fun ragdolls(w: IPhysics3D) {
        ground(w)
        val names = listOf(
            "Hips", "Spine", "Spine1", "Spine2", "Head", "LeftUpLeg", "LeftLeg", "RightUpLeg", "RightLeg",
            "LeftArm", "LeftForeArm", "RightArm", "RightForeArm",
        )
        repeat(50) { r ->
            val root = Bone(0, "mixamorig:${names[0]}")
            names.drop(1).forEachIndexed { i, name -> root.children += Bone(i + 1, "mixamorig:$name") }
            val ragdoll = RagdollBuilder.buildMixamoRagdoll(w, Skeleton(root, names.size))
            val base = Vector3f((r % 10) * 3f, 2f, (r / 10) * 3f)
            ragdoll.boneBodies.values.forEachIndexed { i, bone -> bone.setLocation(Vector3f(base).add(0f, i * 0.35f, 0f)) }
            ragdoll.joints.forEach { w.addConstraint(it) }
            ragdoll.boneBodies.values.forEach { it.isKinematic = false }
        }
    }

    private fun vehicles(w: IPhysics3D): () -> Unit {
        ground(w)
        val solver = RaycastVehicleSolver(w)
        val cars = (0 until 20).map { c ->
            val vehicle = VehicleComponent().apply { throttle = 1f; steering = 0.3f }
            val body = RigidBody3D(1000f)
            GameObject("Car").apply {
                addComponent(Transform(Vector3f((c % 5) * 8f, 1f, (c / 5) * 8f)))
                addComponent(body)
                addComponent(BoxCollider3D(Vector3f(1f, 0.3f, 1.8f)))
                addComponent(vehicle)
                w.add(this)
            }
            vehicle to body
        }
        return { cars.forEach { (vehicle, body) -> solver.step(vehicle, body, dt) } }
    }
}
