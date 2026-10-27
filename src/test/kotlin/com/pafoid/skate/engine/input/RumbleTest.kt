package com.pafoid.skate.engine.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RumbleTest {
    private class Recorder : RumbleBackend {
        override val supported = true
        val calls = mutableListOf<Triple<Int, Float, Float>>()
        override fun setRumble(pad: Int, strong: Float, weak: Float) {
            calls.add(Triple(pad, strong, weak))
        }
    }

    @Test
    fun Rumble_EffectRunsForDurationThenStops() {
        val b = Recorder(); val m = RumbleManager(b)
        m.play(0, 0.8f, 0.3f, 0.5f)
        m.update(0.1f)
        m.update(0.1f)
        assertEquals(listOf(Triple(0, 0.8f, 0.3f)), b.calls) // sent once while unchanged
        m.update(0.4f)
        assertEquals(Triple(0, 0f, 0f), b.calls.last())
        m.update(0.1f)
        assertEquals(2, b.calls.size)
    }

    @Test
    fun Rumble_ValuesAreClampedAndNewEffectReplacesOld() {
        val b = Recorder(); val m = RumbleManager(b)
        m.play(1, 5f, -1f, 1f)
        m.update(0.01f)
        assertEquals(Triple(1, 1f, 0f), b.calls.last())
        m.play(1, 0.5f, 0.5f, 1f)
        m.update(0.01f)
        assertEquals(Triple(1, 0.5f, 0.5f), b.calls.last())
    }

    @Test
    fun Rumble_StopAndInvalidInputs() {
        val b = Recorder(); val m = RumbleManager(b, maxPads = 2)
        m.play(0, 1f, 1f, 1f); m.update(0.01f)
        m.stopAll()
        assertEquals(Triple(0, 0f, 0f), b.calls.last())
        val n = b.calls.size
        m.play(5, 1f); m.play(0, 1f, 1f, 0f); m.update(0.1f)
        assertEquals(n, b.calls.size)
    }

    @Test
    fun Rumble_DefaultBackendIsUnsupportedNoOp() {
        val m = RumbleManager()
        assertFalse(m.supported)
        m.play(0, 1f); m.update(0.1f); m.stopAll()
    }
}
