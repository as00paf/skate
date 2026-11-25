package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.DestructibleComponent
import com.pafoid.skate.engine.ecs.components.DestructionMode
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.Fractured
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PHY-29a end to end: real contacts from [PhysicsSystem] break props through [DestructionSystem]. */
class DestructionPhysicsTest {
    private val events = EventSystem()
    private val scene = Scene()
    private val fractured = mutableListOf<Fractured>()
    private val manager = SystemManager()
    private val dt = 1f / 60f

    private fun box(name: String, at: Vector3f, half: Float, mass: Float) = GameObject(name).apply {
        addComponent(Transform(Vector3f(at)))
        addComponent(RigidBody3D(mass))
        addComponent(BoxCollider3D(Vector3f(half)))
    }.also { scene.addChild(it) }

    private fun start(gravity: Vector3f, prefabs: (String) -> GameObject? = { null }) {
        NativeLibraryLoader().loadNativeLibrary()
        scene.getComponent<ScenePhysicsComponent>()?.gravity?.set(gravity)
            ?: scene.addComponent(ScenePhysicsComponent().apply { this.gravity.set(gravity) })
        events.subscribe<Fractured> { fractured += it }
        manager.addSystem(DestructionSystem(events, prefabs))
        manager.addSystem(PhysicsSystem(mockk<DebugRenderer>(relaxed = true), events))
        manager.loadScene(scene)
        manager.start()
        scene.start()
    }

    @Test
    fun droppedCrate_BreaksOnTheFloorAndLosesItsBody() {
        box("Floor", Vector3f(0f, -0.5f, 0f), 0.5f, 0f).getComponent<Transform>()!!.scale.set(20f, 1f, 20f)
        // 10 kg from 5 m: ≈ 97 N·s at impact, so ≈ 77 damage over the 20 N·s threshold breaks 50 integrity
        val crate = box("Crate", Vector3f(0f, 5.5f, 0f), 0.5f, 10f)
        crate.addComponent(DestructibleComponent(mode = DestructionMode.REMOVE, integrity = 50f, impactThreshold = 20f, impactDamagePerNs = 1f))
        start(Vector3f(0f, -9.81f, 0f))

        repeat(120) { manager.update(dt) }

        val event = fractured.singleOrNull()
        assertNotNull(event, "the crate broke")
        assertEquals(97f, event!!.impulse, 10f)
        assertFalse(crate in scene.children)
        assertEquals(null, crate.getComponent<RigidBody3D>()!!.rawBody, "the body left the world with the object")
    }

    @Test
    fun gentleLanding_LeavesTheCrateIntact() {
        box("Floor", Vector3f(0f, -0.5f, 0f), 0.5f, 0f).getComponent<Transform>()!!.scale.set(20f, 1f, 20f)
        val crate = box("Crate", Vector3f(0f, 0.7f, 0f), 0.5f, 10f) // 0.2 m drop: ≈ 20 N·s, under the default 150
        crate.addComponent(DestructibleComponent(mode = DestructionMode.REMOVE))
        start(Vector3f(0f, -9.81f, 0f))

        repeat(60) { manager.update(dt) }

        assertTrue(fractured.isEmpty())
        assertTrue(crate in scene.children)
        assertEquals(100f, crate.getComponent<DestructibleComponent>()!!.integrity)
    }

    @Test
    fun swappedPrefab_KeepsTheBrokenPropsVelocity() {
        val crate = box("Crate", Vector3f(0f, 0f, 0f), 0.5f, 10f)
        val destructible = DestructibleComponent(mode = DestructionMode.SWAP, brokenPrefab = "broken.prefab")
        crate.addComponent(destructible)
        var broken: GameObject? = null
        start(Vector3f()) {
            GameObject("Broken").apply {
                addComponent(Transform())
                addComponent(RigidBody3D(10f))
                addComponent(BoxCollider3D(Vector3f(0.5f)))
            }.also { broken = it }
        }
        manager.update(dt) // creates the crate's body
        crate.getComponent<RigidBody3D>()!!.linearVelocity = Vector3f(4f, 0f, 0f)
        manager.update(dt)

        manager.getSystem<DestructionSystem>()!!.breakNow(crate, Vector3f())
        repeat(5) { manager.update(dt) }

        val replacement = broken
        assertNotNull(replacement)
        assertTrue(replacement in scene.children)
        assertEquals(4f, replacement!!.getComponent<RigidBody3D>()!!.linearVelocity.x, 0.05f)
        assertTrue(replacement.getComponent<Transform>()!!.translation.x > 0f, "starts where the crate was and keeps moving")
    }
}
