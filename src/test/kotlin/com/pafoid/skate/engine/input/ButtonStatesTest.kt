package com.pafoid.skate.engine.input

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ButtonStatesTest {
    @Test
    fun ButtonStates_ButtonsBeyondFifteenAreTracked() {
        val s = ButtonStates(2)
        s.update(0, { it == 20 }, 24)
        assertTrue(s.isDown(0, 20))
        assertTrue(s.beginPress(0, 20))
    }

    @Test
    fun ButtonStates_BeginPressOnlyOnFirstFrame() {
        val s = ButtonStates(1)
        s.update(0, { it == 3 }, 8)
        s.update(0, { it == 3 }, 8)
        assertTrue(s.isDown(0, 3))
        assertFalse(s.beginPress(0, 3))
    }

    @Test
    fun ButtonStates_ButtonsPastReportedCountAreReleasedAndOutOfRangeIsSafe() {
        val s = ButtonStates(1)
        s.update(0, { true }, 4)
        assertFalse(s.isDown(0, 5))
        assertFalse(s.isDown(0, 99))
        assertFalse(s.isDown(7, 0))
        s.update(9, { true }, 4)
    }

    @Test
    fun ButtonStates_ClearReleasesEverythingWithoutEdges() {
        val s = ButtonStates(1)
        s.update(0, { true }, 4)
        s.clear(0)
        assertFalse(s.isDown(0, 0))
        assertFalse(s.changed(0, 0))
    }
}
