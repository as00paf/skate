package com.pafoid.skate.engine.input

import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputHistoryTest {

    private var x = 0f
    private var y = 0f
    private fun history(capacity: Int = 60) = InputHistory(capacity).also { it.track("stick", { x }, { y }) }

    @Test
    fun InputHistory_VelocityIsDisplacementOverElapsedTimeInWindow() {
        val h = history()
        x = 0f; h.sample(0.0f)
        x = 0.5f; y = -1f; h.sample(0.1f)
        x = 1f; y = -1f; h.sample(0.2f)
        val v = h.velocity("stick", 1f, Vector2f())
        assertEquals(5f, v.x, 1e-4f)
        assertEquals(-5f, v.y, 1e-4f)
        val d = h.displacement("stick", 0.15f)
        assertEquals(0.5f, d.x, 1e-4f, "window starts at the oldest sample within 0.15 s (t = 0.1)")
    }

    @Test
    fun InputHistory_FewerThanTwoSamplesOrUnknownTrackIsZero() {
        val h = history()
        assertEquals(Vector2f(), h.velocity("stick", 1f))
        h.sample(0f)
        assertEquals(Vector2f(), h.velocity("stick", 1f))
        assertEquals(Vector2f(), h.velocity("nope", 1f))
    }

    @Test
    fun InputHistory_SameTimestampGivesZeroVelocity() {
        val h = history()
        x = 0f; h.sample(1f)
        x = 1f; h.sample(1f)
        assertEquals(Vector2f(), h.velocity("stick", 1f))
    }

    @Test
    fun InputHistory_RingBufferKeepsNewestSamples() {
        val h = history(capacity = 3)
        for (i in 0 until 5) { x = i.toFloat(); h.sample(i.toFloat()) }
        assertEquals(3, h.sampleCount("stick"))
        val seen = mutableListOf<Float>()
        h.forEachSample("stick", 100f) { _, sx, _ -> seen.add(sx) }
        assertEquals(listOf(2f, 3f, 4f), seen)
        assertEquals(2f, h.displacement("stick", 100f).x, 1e-6f)
    }

    @Test
    fun InputHistory_UntrackAndClear() {
        val h = history()
        h.sample(0f); h.sample(1f)
        h.clear()
        assertEquals(0, h.sampleCount("stick"))
        h.untrack("stick")
        h.sample(2f)
        assertEquals(emptySet<String>(), h.trackedNames)
    }
}
