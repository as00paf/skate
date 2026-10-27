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

    private fun fallingBodyWorld(): Triple<SystemManager, Transform, PhysicsSystem> {
        NativeLibraryLoader().loadNativeLibrary()
        val scene = Scene()
        val go = GameObject("Ball")
        val transform = Transform().apply { translation.set(0f, 100f, 0f) }
        go.addComponent(transform)
        go.addComponent(RigidBody3D(1f))
        scene.addChild(go)

        val physics = PhysicsSystem(mockk<DebugRenderer>(relaxed = true))
        val manager = SystemManager()
        manager.addSystem(physics)
        manager.loadScene(scene)
        manager.start()
        scene.start()
        return Triple(manager, transform, physics)
    }

    @Test
    fun physicsSystem_RunsInFixedUpdatePhase() {
        NativeLibraryLoader().loadNativeLibrary()
        val physics = PhysicsSystem(mockk<DebugRenderer>(relaxed = true))

        assertEquals(SystemPhase.FIXED_UPDATE, physics.phase)
    }

    @Test
    fun physicsSystem_FrameShorterThanFixedStep_DoesNotStep() {
        val (manager, transform, _) = fallingBodyWorld()
        manager.update(1f / 60f) // first step creates the body
        val y = transform.translation.y

        manager.update(1f / 240f)

        assertEquals(y, transform.translation.y, 0f)
    }

    @Test
    fun physicsSystem_SameSimulatedTime_IsIndependentOfFrameRate() {
        val (slowManager, slowTransform, _) = fallingBodyWorld()
        val (fastManager, fastTransform, _) = fallingBodyWorld()

        repeat(30) { slowManager.update(1f / 30f) }
        repeat(120) { fastManager.update(1f / 120f) }

        assertTrue(slowTransform.translation.y < 100f)
        assertEquals(slowTransform.translation.y, fastTransform.translation.y, 0.05f)
    }
}
