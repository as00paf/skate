package com.pafoid.skate.engine.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GamepadHotPlugTest {
    @Test
    fun HotPlug_EventsFireOnlyOnRealChanges() {
        val r = GamepadRegistry()
        val events = mutableListOf<GamepadConnectionEvent>()
        r.addListener { events.add(it) }
        assertTrue(r.setPresent(0, true) { "Pad" })
        assertFalse(r.setPresent(0, true)) // callback + polling reporting the same connect
        assertTrue(r.setPresent(0, false))
        assertFalse(r.setPresent(0, false))
        assertEquals(listOf(GamepadConnectionEvent(0, true, "Pad"), GamepadConnectionEvent(0, false, null)), events)
    }

    @Test
    fun HotPlug_ReplugIntoOtherSlotStillResolvesAsPrimaryPad() {
        val r = GamepadRegistry()
        r.setPresent(0, true)
        assertEquals(0, r.joystickForPad(0))
        r.setPresent(0, false)
        assertEquals(-1, r.joystickForPad(0))
        r.setPresent(2, true)
        assertEquals(2, r.joystickForPad(0))
        assertEquals(-1, r.joystickForPad(1))
    }

    @Test
    fun HotPlug_SecondPadBecomesPrimaryWhenFirstIsUnplugged() {
        val r = GamepadRegistry()
        r.setPresent(0, true); r.setPresent(1, true)
        assertEquals(1, r.joystickForPad(1))
        r.setPresent(0, false)
        assertEquals(1, r.joystickForPad(0))
    }

    @Test
    fun HotPlug_OutOfRangeSlotsAreIgnored() {
        val r = GamepadRegistry(4)
        assertFalse(r.setPresent(9, true))
        assertFalse(r.setPresent(-1, true))
        assertFalse(r.isPresent(9))
    }
}
