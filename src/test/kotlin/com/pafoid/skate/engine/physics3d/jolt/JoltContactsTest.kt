package com.pafoid.skate.engine.physics3d.jolt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JoltContactsTest {

    @Test
    fun JoltContacts_PairKey_IsOrderIndependentAndUnsigned() {
        val key = JoltContacts.pairKey(7, 3)
        assertEquals(key, JoltContacts.pairKey(3, 7))
        assertEquals(3, JoltContacts.lowId(key))
        assertEquals(7, JoltContacts.highId(key))
        assertEquals(0L, JoltContacts.pairKey(5, 5))

        // Jolt body IDs use the sign bit for their sequence number; they must still order as unsigned
        val high = JoltContacts.pairKey(-1, 1)
        assertEquals(1, JoltContacts.lowId(high))
        assertEquals(-1, JoltContacts.highId(high))
    }

    @Test
    fun JoltContacts_Impulse_IsApproachSpeedTimesEffectiveMass() {
        // 2 kg box hitting static ground at 4 m/s
        assertEquals(8f, JoltContacts.impulse(-4f, 0.5f, 0f), 1e-5f)
        // Two 2 kg bodies: effective mass 1 kg
        assertEquals(4f, JoltContacts.impulse(-4f, 0.5f, 0.5f), 1e-5f)
        // Separating, or two static bodies: no impulse
        assertEquals(0f, JoltContacts.impulse(3f, 0.5f, 0f))
        assertEquals(0f, JoltContacts.impulse(-3f, 0f, 0f))
    }
}
