package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.EquipmentComponent
import com.pafoid.skate.engine.ecs.components.HealthComponent
import com.pafoid.skate.engine.events.DamagedEvent
import com.pafoid.skate.engine.events.DiedEvent
import com.pafoid.skate.engine.events.HitEvent
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.gameplay.items.ItemDef
import com.pafoid.skate.engine.gameplay.items.ItemStack
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24b §4.4: hits through a real [EventSystem] lower health, report damage and death once. */
class HealthSystemTest {
    private val events = EventSystem()
    private val items = ItemDatabase().apply { register(ItemDef("fire.vest", stats = mapOf("resist.fire" to 0.5f))) }
    private val system = HealthSystem(events, items)
    private val damaged = mutableListOf<DamagedEvent>()
    private val died = mutableListOf<DiedEvent>()

    init {
        events.subscribe<DamagedEvent> { damaged += it }
        events.subscribe<DiedEvent> { died += it }
    }

    private fun dummy(max: Float = 100f) = GameObject("Dummy").addComponent(HealthComponent(max))

    @Test
    fun Health_AHitLowersHealthAndPublishesDamagedSynchronously() {
        val target = dummy()
        events.publish(HitEvent(null, target, 30f))
        assertEquals(70f, target.getComponent<HealthComponent>()!!.current)
        assertEquals(listOf(DamagedEvent(target, 30f, 70f)), damaged, "applied before publish returns")
        assertTrue(died.isEmpty())
    }

    @Test
    fun Health_ReachingZeroPublishesDiedExactlyOnceAndDeadTargetsIgnoreHits() {
        val attacker = GameObject("Attacker")
        val target = dummy(max = 50f)
        events.publish(HitEvent(attacker, target, 30f))
        events.publish(HitEvent(attacker, target, 30f))
        events.publish(HitEvent(attacker, target, 30f))
        val health = target.getComponent<HealthComponent>()!!
        assertEquals(0f, health.current)
        assertTrue(health.dead)
        assertEquals(listOf(DiedEvent(target, attacker)), died)
        assertEquals(listOf(30f, 20f), damaged.map { it.applied }, "the killing hit reports only what was left; later hits none")
    }

    @Test
    fun Health_EquipmentResistanceReducesItsDamageType() {
        val target = dummy().addComponent(EquipmentComponent(linkedMapOf("TORSO" to ItemStack("fire.vest", 1))))
        target.getComponent<HealthComponent>()!!.resistances["fire"] = 0.2f
        events.publish(HitEvent(null, target, 40f, "fire"))
        events.publish(HitEvent(null, target, 40f, "physical"))
        assertEquals(listOf(12f, 40f), damaged.map { it.applied })
    }

    @Test
    fun Health_ObjectsWithoutHealthAreNotDamaged() {
        events.publish(HitEvent(null, GameObject("Rock"), 10f))
        assertTrue(damaged.isEmpty())
    }

    @Test
    fun ComponentRegistry_HealthInstantiatesMapsBackAndRoundTrips() {
        assertTrue(ComponentType.HEALTH.instantiate() is HealthComponent)
        assertEquals(ComponentType.HEALTH, HealthComponent().type)
        val health = HealthComponent(80f, 35f).apply { resistances["fire"] = 0.25f }
        val serializer = Serializer()
        val copy = serializer.decode<Component>(serializer.encode<Component>(health)) as HealthComponent
        assertEquals(listOf(80f, 35f), listOf(copy.max, copy.current))
        assertEquals(mapOf("fire" to 0.25f), copy.resistances)
    }
}
