package com.pafoid.skate.engine.physics3d.jolt

import com.pafoid.skate.engine.physics3d.ALL_LAYERS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayerSlotsTest {

    @Test
    fun LayerSlots_SamePairReusesItsSlot() {
        val slots = LayerSlots(8)
        val a = slots.slotFor(1, ALL_LAYERS)
        assertEquals(a, slots.slotFor(1, ALL_LAYERS))
        assertEquals(1, slots.size)
        assertEquals(1, slots.slotFor(2, ALL_LAYERS))
    }

    @Test
    fun LayerSlots_NewSlotReportsOnlyCollidingPairs_IncludingItself() {
        val slots = LayerSlots(8)
        val pairs = mutableListOf<Pair<Int, Int>>()
        val ground = slots.slotFor(1, ALL_LAYERS) { a, b -> pairs += a to b }
        assertEquals(listOf(ground to ground), pairs)

        pairs.clear()
        val ghost = slots.slotFor(2, 4) { a, b -> pairs += a to b } // collides with layer 4 only
        assertEquals(emptyList<Pair<Int, Int>>(), pairs)

        pairs.clear()
        val box = slots.slotFor(2, ALL_LAYERS) { a, b -> pairs += a to b }
        assertEquals(listOf(box to ground, box to box), pairs)
        assertFalse(slots.collides(ghost, box))
    }

    @Test
    fun LayerSlots_RuleNeedsBothMasksToAccept() {
        val slots = LayerSlots(8)
        val a = slots.slotFor(1, 2)
        val b = slots.slotFor(2, 1)
        val c = slots.slotFor(2, 4)
        assertTrue(slots.collides(a, b))
        assertTrue(slots.collides(b, a))
        assertFalse(slots.collides(a, c))
    }

    @Test
    fun LayerSlots_NegativeMasksAreDistinctKeys() {
        val slots = LayerSlots(8)
        assertTrue(slots.slotFor(1, -1) != slots.slotFor(1, Int.MAX_VALUE))
        assertEquals(-1, slots.maskOf(0))
    }

    @Test
    fun LayerSlots_FullTable_Throws() {
        val slots = LayerSlots(2)
        slots.slotFor(1, 1)
        slots.slotFor(2, 2)
        assertThrows(IllegalStateException::class.java) { slots.slotFor(4, 4) }
    }

    @Test
    fun LayerSlots_ObjectLayers_EvenStaticOddMoving() {
        assertEquals(6, LayerSlots.objectLayer(3, moving = false))
        assertEquals(7, LayerSlots.objectLayer(3, moving = true))
        assertEquals(3, LayerSlots.slotOf(7))
    }
}
