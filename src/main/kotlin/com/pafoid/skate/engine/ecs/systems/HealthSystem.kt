package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.components.EquipmentComponent
import com.pafoid.skate.engine.ecs.components.HealthComponent
import com.pafoid.skate.engine.events.DamagedEvent
import com.pafoid.skate.engine.events.DiedEvent
import com.pafoid.skate.engine.events.HitEvent
import com.pafoid.skate.engine.gameplay.combat.DamageMath
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.getComponent

/**
 * Applies [HitEvent]s to [HealthComponent]s (GPL-24 §4.3). Damage is applied synchronously in the subscriber
 * (decision Q5): `EventSystem.publish` is synchronous, so the order of hits is the order they were published.
 * Resistances add the `resist.<type>` stats of the target's equipment from [items]. Publishes [DamagedEvent] for
 * every hit that lands, then [DiedEvent] once when health reaches 0; hits on a dead target are ignored.
 */
class HealthSystem(private val events: EventSystem, private val items: ItemDatabase = ItemDatabase()) : System() {

    init {
        events.subscribe<HitEvent> { apply(it) }
    }

    private fun apply(hit: HitEvent) {
        val health = hit.target.getComponent<HealthComponent>() ?: return
        if (health.dead) return
        val worn = hit.target.getComponent<EquipmentComponent>()?.equipped?.values.orEmpty()
        val amount = DamageMath.resolve(hit.amount, hit.type, health.resistances, DamageMath.equipmentStats(worn, items))
        val applied = minOf(amount, health.current.coerceAtLeast(0f))
        health.current = (health.current - amount).coerceAtLeast(0f)
        events.publish(DamagedEvent(hit.target, applied, health.current))
        if (health.current <= 0f) {
            health.dead = true
            events.publish(DiedEvent(hit.target, hit.attacker))
        }
    }
}
