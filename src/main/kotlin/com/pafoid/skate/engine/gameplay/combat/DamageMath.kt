package com.pafoid.skate.engine.gameplay.combat

import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.gameplay.items.ItemStack

/** Pure damage resolution (GPL-24 §4.1). Damage types are free-form strings, not an engine enum. */
object DamageMath {
    /** Resistance never blocks more than this share of a hit. */
    const val MAX_RESISTANCE = 0.9f

    /** Item stat prefix for resistances: `resist.fire` = 0.3 resists 30 % of fire damage. */
    const val RESIST_PREFIX = "resist."

    /** Base resistance to [type] plus the `resist.<type>` stats of the equipment, clamped to [0, [MAX_RESISTANCE]]. */
    fun resistance(type: String, resistances: Map<String, Float>, equipmentStats: Map<String, Float>): Float =
        ((resistances[type] ?: 0f) + (equipmentStats[RESIST_PREFIX + type] ?: 0f)).coerceIn(0f, MAX_RESISTANCE)

    /** Damage that gets through: [base] (negative counts as 0) reduced by the [type] resistance. */
    fun resolve(base: Float, type: String, resistances: Map<String, Float>, equipmentStats: Map<String, Float>): Float =
        base.coerceAtLeast(0f) * (1f - resistance(type, resistances, equipmentStats))

    /** Summed stats of the worn items ([ItemDatabase] definitions); unknown items add nothing. */
    fun equipmentStats(equipped: Collection<ItemStack>, items: ItemDatabase): Map<String, Float> {
        val out = HashMap<String, Float>()
        for (stack in equipped) items[stack.itemId]?.stats?.forEach { (k, v) -> out[k] = (out[k] ?: 0f) + v }
        return out
    }
}
