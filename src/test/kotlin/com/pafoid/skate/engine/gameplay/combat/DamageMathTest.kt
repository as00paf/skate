package com.pafoid.skate.engine.gameplay.combat

import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.gameplay.items.ItemDef
import com.pafoid.skate.engine.gameplay.items.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** GPL-24b §4.4: resistances from the body and the equipment, and the clamp. */
class DamageMathTest {

    @Test
    fun DamageMath_FireArmourReducesFireDamageAndLeavesPhysicalUnchanged() {
        val armour = mapOf("resist.fire" to 0.3f)
        assertEquals(70f, DamageMath.resolve(100f, "fire", emptyMap(), armour), 1e-4f)
        assertEquals(100f, DamageMath.resolve(100f, "physical", emptyMap(), armour), 1e-4f)
    }

    @Test
    fun DamageMath_BodyAndEquipmentResistancesAddUpToTheClamp() {
        assertEquals(50f, DamageMath.resolve(100f, "fire", mapOf("fire" to 0.2f), mapOf("resist.fire" to 0.3f)), 1e-4f)
        assertEquals(10f, DamageMath.resolve(100f, "fire", mapOf("fire" to 0.8f), mapOf("resist.fire" to 0.8f)), 1e-4f, "clamped at 0.9")
        assertEquals(100f, DamageMath.resolve(100f, "fire", mapOf("fire" to -0.5f), emptyMap()), 1e-4f, "never below 0")
        assertEquals(0f, DamageMath.resolve(-20f, "fire", emptyMap(), emptyMap()), "negative hits do nothing")
    }

    @Test
    fun DamageMath_EquipmentStatsSumOverWornItems() {
        val db = ItemDatabase().apply {
            register(ItemDef("helmet", stats = mapOf("resist.physical" to 0.2f)))
            register(ItemDef("vest", stats = mapOf("resist.physical" to 0.1f, "resist.fire" to 0.4f)))
        }
        val stats = DamageMath.equipmentStats(listOf(ItemStack("helmet", 1), ItemStack("vest", 1), ItemStack("unknown", 1)), db)
        assertEquals(0.3f, stats["resist.physical"]!!, 1e-6f)
        assertEquals(0.4f, stats["resist.fire"]!!, 1e-6f)
    }
}
