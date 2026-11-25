package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DestructibleComponent
import com.pafoid.skate.engine.ecs.components.DestructionMode
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.Collision
import com.pafoid.skate.engine.events.DestructibleDamaged
import com.pafoid.skate.engine.events.Fractured
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.ContactImpulse
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PHY-29a: impact damage, integrity, the break queue, REMOVE and SWAP (`docs/design/PHY-29.md` §5, §13). */
class DestructionSystemTest {
    private val events = EventSystem()
    private val scene = Scene()
    private val damaged = mutableListOf<DestructibleDamaged>()
    private val fractured = mutableListOf<Fractured>()
    private val warnings = mutableListOf<String>()
    private val prefabs = mutableMapOf<String, () -> GameObject>()
    private val system = DestructionSystem(events, { ref -> prefabs[ref]?.invoke() }, { warnings += it })
    private val dt = 1f / 60f
    private val wall = GameObject("Wall").also { scene.addChild(it) }

    init {
        events.subscribe<DestructibleDamaged> { damaged += it }
        events.subscribe<Fractured> { fractured += it }
        system.init(scene)
        scene.start()
    }

    private fun prop(name: String, mode: DestructionMode = DestructionMode.REMOVE, configure: DestructibleComponent.() -> Unit = {}) =
        GameObject(name).apply {
            addComponent(Transform(Vector3f(1f, 2f, 3f)))
            addComponent(DestructibleComponent(mode = mode).apply(configure))
        }.also { scene.addChild(it) }

    private fun hit(target: GameObject, impulse: Float, other: GameObject = wall, point: Vector3f = Vector3f(1f, 2f, 3f)) =
        events.publish(Collision(target, other, point, Vector3f(0f, 1f, 0f), impulse))

    @Test
    fun runsInTheFixedStepAfterPhysics() {
        assertEquals(SystemPhase.FIXED_UPDATE, system.phase)
        assertTrue(PhysicsSystem::class in system.runsAfter)
    }

    @Test
    fun impactBelowThreshold_DoesNothing() {
        val crate = prop("Crate")

        hit(crate, 150f)
        system.fixedUpdate(dt)

        assertTrue(damaged.isEmpty())
        assertEquals(100f, crate.getComponent<DestructibleComponent>()!!.integrity)
    }

    @Test
    fun impactAboveThreshold_DealsDamagePerNsOverTheThreshold() {
        val crate = prop("Crate")

        hit(crate, 250f)
        system.fixedUpdate(dt)

        val event = damaged.single()
        assertSame(crate, event.target)
        assertEquals(50f, event.amount, 1e-4f)
        assertEquals(50f, event.remaining, 1e-4f)
        assertEquals("impact", event.type)
        assertTrue(fractured.isEmpty())
    }

    @Test
    fun severalContactsInOneStep_CountOnceWithTheLargest() {
        val crate = prop("Crate")

        hit(crate, 200f)
        hit(crate, 260f, point = Vector3f(9f, 9f, 9f))
        hit(crate, 180f)
        system.fixedUpdate(dt)

        val event = damaged.single()
        assertEquals(55f, event.amount, 1e-4f)
        assertEquals(Vector3f(9f, 9f, 9f), event.point)
    }

    @Test
    fun defaults_SkaterBreaksACrate_ThrownCrateDoesNot() {
        val skaterImpulse = ContactImpulse.of(-6f, 1f / 70f, 0f) // 70 kg at 6 m/s against a static prop: 420 N·s
        val crateImpulse = ContactImpulse.of(-5f, 1f / 20f, 0f) // 20 kg crate thrown at 5 m/s: 100 N·s
        val hitBySkater = prop("A", DestructionMode.REMOVE)
        val hitByCrate = prop("B", DestructionMode.REMOVE)

        hit(hitBySkater, skaterImpulse)
        hit(hitByCrate, crateImpulse)
        system.fixedUpdate(dt)

        assertEquals(listOf(hitBySkater), fractured.map { it.target })
        assertEquals(100f, hitByCrate.getComponent<DestructibleComponent>()!!.integrity)
    }

    @Test
    fun bothSidesOfAContact_CanBeDestructible() {
        val a = prop("A")
        val b = prop("B")

        hit(a, 250f, other = b)
        hit(b, 250f, other = a)
        system.fixedUpdate(dt)

        assertEquals(setOf(a, b), damaged.map { it.target }.toSet())
    }

    @Test
    fun integrityReachesZero_BreaksOnceAndRemovesTheProp() {
        val crate = prop("Crate") { surface = "glass" }

        hit(crate, 400f)
        system.fixedUpdate(dt)
        hit(crate, 400f)
        system.fixedUpdate(dt)

        val event = fractured.single()
        assertSame(crate, event.target)
        assertEquals(400f, event.impulse)
        assertEquals("glass", event.surface)
        assertEquals(DestructionMode.REMOVE, event.mode)
        assertEquals(0, event.pieces)
        assertTrue(crate.getComponent<DestructibleComponent>()!!.broken)
        assertTrue(crate.isDead)
        assertFalse(crate in scene.children)
    }

    @Test
    fun fractured_IsPublishedWhileThePropIsStillInTheScene() {
        val crate = prop("Crate")
        var inSceneDuringEvent = false
        events.subscribe<Fractured> { inSceneDuringEvent = crate in scene.children && !crate.isDead }

        system.breakNow(crate, Vector3f())
        system.fixedUpdate(dt)

        assertTrue(inSceneDuringEvent)
    }

    @Test
    fun queuedProp_NeverBreaksTwice() {
        val crate = prop("Crate")

        assertTrue(system.breakNow(crate, Vector3f()))
        assertFalse(system.breakNow(crate, Vector3f()))
        assertFalse(system.damage(crate, 10f, Vector3f()))
        hit(crate, 1000f)
        repeat(3) { system.fixedUpdate(dt) }

        assertEquals(1, fractured.size)
        assertTrue(damaged.isEmpty())
        assertFalse(system.breakNow(crate, Vector3f()), "a broken prop can't break again")
    }

    @Test
    fun atMostFourBreaksPerStep_TheRestWaitInOrder() {
        val props = (1..6).map { prop("P$it") }
        props.forEach { system.breakNow(it, Vector3f()) }

        system.fixedUpdate(dt)
        assertEquals(props.take(DestructionSystem.MAX_BREAKS_PER_STEP), fractured.map { it.target })
        assertEquals(2, system.queuedBreaks)

        system.fixedUpdate(dt)
        assertEquals(props, fractured.map { it.target })
        assertEquals(0, system.queuedBreaks)
    }

    @Test
    fun damageApi_LowersIntegrityWithTheCallersType() {
        val crate = prop("Crate")

        assertTrue(system.damage(crate, 30f, Vector3f(), "fire"))
        assertTrue(system.damage(crate, 80f, Vector3f(), "fire"))
        system.fixedUpdate(dt)

        assertEquals(listOf(70f, 0f), damaged.map { it.remaining })
        assertEquals("fire", damaged.first().type)
        assertEquals(0f, fractured.single().impulse)
    }

    @Test
    fun damageApi_RejectsNonDestructiblesAndBadAmounts() {
        val crate = prop("Crate")
        val plain = GameObject("Plain").also { scene.addChild(it) }
        val detached = GameObject("Detached").apply { addComponent(DestructibleComponent()) }

        assertFalse(system.damage(plain, 10f, Vector3f()))
        assertFalse(system.damage(detached, 10f, Vector3f()))
        assertFalse(system.damage(crate, 0f, Vector3f()))
        assertFalse(system.damage(crate, Float.NaN, Vector3f()))
        crate.getComponent<DestructibleComponent>()!!.enabled = false
        assertFalse(system.damage(crate, 10f, Vector3f()))
        assertTrue(damaged.isEmpty())
    }

    @Test
    fun collisionsWhileNotRunning_AreIgnored() {
        val crate = prop("Crate")
        scene.isRunning = false

        hit(crate, 1000f)
        scene.isRunning = true
        system.fixedUpdate(dt)

        assertTrue(damaged.isEmpty())
    }

    @Test
    fun swap_PutsTheBrokenPrefabInPlaceWithTheSameTransform() {
        val before = GameObject("Before").also { scene.addChild(it) }
        val crate = prop("Crate", DestructionMode.SWAP) { brokenPrefab = "crate_broken.prefab" }
        crate.getComponent<Transform>()!!.apply { rotation.set(0f, 45f, 0f); scale.set(2f) }
        GameObject("After").also { scene.addChild(it) }
        prefabs["crate_broken.prefab"] = { GameObject("Broken").apply { addComponent(Transform()) } }

        system.breakNow(crate, Vector3f())
        system.fixedUpdate(dt)

        val index = scene.children.indexOf(before) + 1
        val broken = scene.children[index]
        assertEquals("Broken", broken.name)
        val transform = broken.getComponent<Transform>()!!
        assertEquals(Vector3f(1f, 2f, 3f), transform.translation)
        assertEquals(Vector3f(0f, 45f, 0f), transform.rotation)
        assertEquals(Vector3f(2f), transform.scale)
        assertTrue(crate.isDead)
        val event = fractured.single()
        assertEquals(DestructionMode.SWAP, event.mode)
        assertEquals(1, event.pieces)
    }

    @Test
    fun swapWithMissingPrefab_IsRemovedAndWarnsOnce() {
        val a = prop("A", DestructionMode.SWAP) { brokenPrefab = "missing.prefab" }
        val b = prop("B", DestructionMode.SWAP) { brokenPrefab = "missing.prefab" }

        system.breakNow(a, Vector3f())
        system.breakNow(b, Vector3f())
        system.fixedUpdate(dt)

        assertEquals(listOf(DestructionMode.REMOVE, DestructionMode.REMOVE), fractured.map { it.mode })
        assertFalse(a in scene.children || b in scene.children)
        assertEquals(1, warnings.size)
    }

    @Test
    fun shatter_BreaksLikeARemovalUntilDebrisExists() {
        val statue = prop("Statue", DestructionMode.SHATTER)

        system.breakNow(statue, Vector3f())
        system.fixedUpdate(dt)

        assertEquals(DestructionMode.REMOVE, fractured.single().mode)
        assertFalse(statue in scene.children)
    }

    @Test
    fun listenerRemovingThePropOnFractured_IsTolerated() {
        val crate = prop("Crate", DestructionMode.SWAP) { brokenPrefab = "b.prefab" }
        prefabs["b.prefab"] = { GameObject("Broken") }
        events.subscribe<Fractured> { scene.removeChild(it.target) }

        system.breakNow(crate, Vector3f())
        system.fixedUpdate(dt)

        assertEquals(1, fractured.size)
        assertTrue(scene.children.none { it.name == "Broken" }, "the swap is dropped when the prop is already gone")
    }

    @Test
    fun loadingAScene_ForgetsQueuedBreaks() {
        val crate = prop("Crate")
        system.breakNow(crate, Vector3f())

        system.init(scene)
        system.fixedUpdate(dt)

        assertTrue(fractured.isEmpty())
        assertEquals(0f, crate.getComponent<DestructibleComponent>()!!.integrity)
    }
}
