package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhysicsSystemFixedStepTest {

    private data class World(val manager: SystemManager, val transform: Transform, val body: RigidBody3D, val physics: PhysicsSystem)

    private fun fallingBodyWorld(): World {
        NativeLibraryLoader().loadNativeLibrary()
        val scene = Scene()
        val go = GameObject("Ball")
        val transform = Transform().apply { translation.set(0f, 100f, 0f) }
        go.addComponent(transform)
        val body = RigidBody3D(1f)
        go.addComponent(body)
        scene.addChild(go)

        val physics = PhysicsSystem(mockk<DebugRenderer>(relaxed = true))
        val manager = SystemManager()
        manager.addSystem(physics)
        manager.loadScene(scene)
        manager.start()
        scene.start()
        return World(manager, transform, body, physics)
    }

    @Test
    fun physicsSystem_RunsInFixedUpdatePhase() {
        NativeLibraryLoader().loadNativeLibrary()
        val physics = PhysicsSystem(mockk<DebugRenderer>(relaxed = true))

        assertEquals(SystemPhase.FIXED_UPDATE, physics.phase)
    }

    @Test
    fun physicsSystem_FrameShorterThanFixedStep_DoesNotStep() {
        val world = fallingBodyWorld()
        world.manager.update(1f / 60f) // first step creates the body
        world.manager.update(1f / 60f)
        val y = world.body.getWorldPosition().y

        world.manager.update(1f / 240f)

        assertEquals(y, world.body.getWorldPosition().y, 0f)
    }

    @Test
    fun physicsSystem_SameSimulatedTime_IsIndependentOfFrameRate() {
        val slow = fallingBodyWorld()
        val fast = fallingBodyWorld()

        repeat(30) { slow.manager.update(1f / 30f) }
        repeat(120) { fast.manager.update(1f / 120f) }

        assertTrue(slow.body.getWorldPosition().y < 100f)
        assertEquals(slow.body.getWorldPosition().y, fast.body.getWorldPosition().y, 0.05f)
    }

    @Test
    fun physicsSystem_OnFrame_BlendsBetweenLastTwoFixedSteps() {
        val (_, transform, _, physics) = fallingBodyWorld()
        physics.fixedUpdate(1f / 60f)
        physics.fixedUpdate(1f / 60f)
        physics.fixedUpdate(1f / 60f)

        physics.onFrame(0f, 0f)
        val previous = transform.translation.y
        physics.onFrame(0f, 1f)
        val current = transform.translation.y
        physics.onFrame(0f, 0.5f)

        assertTrue(current < previous)
        assertEquals((previous + current) / 2f, transform.translation.y, 1e-4f)
    }
}
