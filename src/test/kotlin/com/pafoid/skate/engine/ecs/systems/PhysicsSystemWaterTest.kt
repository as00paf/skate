package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.environment.WaterSample
import com.pafoid.skate.engine.events.WaterEntered
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PHY-16: PhysicsSystem floats scene bodies in scene water and publishes water events while the scene runs. */
class PhysicsSystemWaterTest {

    @Test
    fun runningScene_FloatsACrateInAPool_AndPublishesWaterEntered() {
        NativeLibraryLoader().loadNativeLibrary()
        val events = EventSystem()
        val entered = mutableListOf<WaterEntered>()
        events.subscribe<WaterEntered> { entered += it }
        val physics = PhysicsSystem(mockk<DebugRenderer>(relaxed = true), events)
        val manager = SystemManager().apply { addSystem(physics); start() }

        val pool = GameObject("Pool").apply {
            addComponent(Transform(Vector3f(0f, 1f, 0f)).also { it.worldMatrix.translation(0f, 1f, 0f) })
            addComponent(WaterComponent(sizeX = 6f, sizeZ = 6f, depth = 3f, waves = mutableListOf()))
        }
        val crate = GameObject("Crate").apply {
            addComponent(Transform(Vector3f(0f, 2f, 0f)))
            addComponent(RigidBody3D(400f))
            addComponent(BoxCollider3D(Vector3f(0.5f)))
        }
        val scene = Scene().apply {
            addComponent(ScenePhysicsComponent())
            addChild(pool)
            addChild(crate)
        }
        manager.loadScene(scene)
        scene.isRunning = true

        repeat(600) { manager.update(1f / 60f) }

        val y = crate.getComponent<RigidBody3D>()!!.getWorldPosition().y
        // 400 kg/m³: 40 % under, centre 0.1 m above the surface at y = 1
        assertEquals(1.1f, y, 0.06f)
        assertEquals(listOf(WaterEntered(pool, crate)), entered)
        val sample = WaterSample()
        assertTrue(physics.waterQueries.sample(0f, 0f, 0f, sample))
        assertEquals(1f, sample.surfaceY, 1e-5f)
    }
}
