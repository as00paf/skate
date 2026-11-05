package com.pafoid.skate.engine.render.rhi.opengl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** REN-22: per-pass GPU timings come from timer queries read later, never by waiting on the GPU. */
class GpuTimerRingTest {

    /** Fake GL: query results become available [latency] frames after they end. */
    private class FakeQueries(private val latency: Int) : GpuQueryApi {
        var next = 1
        var frame = 0
        val endedAt = HashMap<Int, Int>()
        val value = HashMap<Int, Long>()
        var active = 0
        var nextResult = 0L
        override fun create() = next++
        override fun begin(query: Int) { active = query }
        override fun end() { endedAt[active] = frame; value[active] = nextResult }
        override fun isAvailable(query: Int) = endedAt[query]?.let { frame - it >= latency } ?: false
        override fun resultNs(query: Int): Long {
            check(isAvailable(query)) { "read before available: would stall" }
            return value.getValue(query)
        }
        override fun delete(query: Int) {}
    }

    @Test
    fun GpuTimerRing_ReportsResultsWhenAvailableWithoutBlocking() {
        val api = FakeQueries(latency = 2)
        val ring = GpuTimerRing(api)
        for (f in 0L until 6L) {
            api.frame = f.toInt()
            api.nextResult = 1000L * (f + 1)
            ring.begin(f); ring.end(f)
            ring.collect()
            val expected = if (f < 2) -1L else 1000L * (f - 1)
            assertEquals(expected, ring.latestNs, "frame $f")
        }
    }

    @Test
    fun GlTimer_EachSpanIsOneSample_ReadWhenItEnds() {
        val api = FakeQueries(latency = 0)
        val timer = GlTimer(GpuTimerRing(api))
        assertEquals(-1L, timer.latestNs)
        api.nextResult = 750_000L
        timer.begin(); timer.end()
        assertEquals(750_000L, timer.latestNs)
        api.nextResult = 900_000L
        timer.begin(); timer.end()
        assertEquals(900_000L, timer.latestNs)
        timer.close(); timer.close()
    }
}
