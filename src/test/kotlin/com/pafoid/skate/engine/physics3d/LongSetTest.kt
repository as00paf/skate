package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.bullet.LongSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LongSetTest {

    @Test
    fun longSet_AddReportsNewKeysOnly() {
        val set = LongSet()

        assertTrue(set.add(5L))
        assertFalse(set.add(5L))
        assertTrue(set.contains(5L))
        assertFalse(set.contains(6L))
    }

    @Test
    fun longSet_GrowsAndKeepsAllKeys() {
        val set = LongSet(4)

        (1L..500L).forEach { set.add(it shl 32 or it) }

        assertEquals(500, set.size)
        assertTrue((1L..500L).all { set.contains(it shl 32 or it) })
    }

    @Test
    fun longSet_ClearAndRetainWhere_DropKeys() {
        val set = LongSet()
        (1L..10L).forEach { set.add(it) }

        set.retainWhere { it % 2 == 0L }
        assertEquals(5, set.size)
        assertFalse(set.contains(3L))

        set.clear()
        assertEquals(0, set.size)
        assertFalse(set.contains(2L))
    }
}
