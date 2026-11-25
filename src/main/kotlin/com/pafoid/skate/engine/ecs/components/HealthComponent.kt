package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

/**
 * Hit points of anything that can be damaged (GPL-24 §4.1). Plain data: damage arrives as a `HitEvent` and
 * `HealthSystem` applies it. [resistances] are per damage type (`physical`, `fire`, …), 0..0.9 after equipment.
 */
@Serializable
class HealthComponent(
    var max: Float = 100f,
    var current: Float = max,
    var dead: Boolean = false,
    val resistances: MutableMap<String, Float> = linkedMapOf(),
) : Component()
