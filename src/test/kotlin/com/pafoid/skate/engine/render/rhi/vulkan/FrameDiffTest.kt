package com.pafoid.skate.engine.render.rhi.vulkan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FrameDiffTest {
    private fun frame(vararg rgb: Int): ByteArray =
        ByteArray(rgb.size / 3 * 4).also { b -> for (p in 0 until rgb.size / 3) { for (c in 0 until 3) b[p * 4 + c] = rgb[p * 3 + c].toByte(); b[p * 4 + 3] = -1 } }

    @Test
    fun FrameDiff_IdenticalFramesHaveNoDifference() {
        val a = frame(10, 20, 30, 200, 100, 0)
        val d = FrameDiff(a, a.copyOf())
        assertEquals(0.0, d.meanPerChannel)
        assertEquals(0, d.max)
        assertEquals(1.0, d.within(0))
    }

    @Test
    fun FrameDiff_PixelDifferenceIsTheLargestChannel() {
        val d = FrameDiff(frame(0, 0, 0, 255, 255, 255), frame(1, 3, 0, 255, 245, 255))
        assertEquals(10, d.max)
        assertEquals(listOf(3, 10), d.perPixel.toList())
        assertEquals((1 + 3 + 10) / 6.0, d.meanPerChannel, 1e-9)
        assertEquals(0.5, d.within(3))
        assertEquals(1.0, d.within(10))
    }

    @Test
    fun FrameDiff_IgnoresAlpha() {
        val a = frame(5, 5, 5)
        val b = a.copyOf().also { it[3] = 0 }
        assertEquals(0, FrameDiff(a, b).max)
    }

    @Test
    fun FrameDiff_HandlesUnsignedBytes() {
        assertEquals(255, FrameDiff(frame(0, 0, 0), frame(255, 0, 0)).max)
    }

    @Test
    fun FrameDiff_ToleranceChecksMeanAndShare() {
        val d = FrameDiff(frame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), frame(0, 0, 0, 0, 0, 0, 0, 0, 0, 9, 0, 0))
        assertTrue(d.meets(FrameDiff.Tolerance(maxMean = 1.0, channel = 2, minShareWithin = 0.75)))
        assertFalse(d.meets(FrameDiff.Tolerance(maxMean = 1.0, channel = 2, minShareWithin = 0.8)))
        assertFalse(d.meets(FrameDiff.Tolerance(maxMean = 0.5, channel = 9, minShareWithin = 0.0)))
    }

    @Test
    fun FrameDiff_RejectsMismatchedSizes() {
        assertThrows<IllegalArgumentException> { FrameDiff(frame(0, 0, 0), frame(0, 0, 0, 0, 0, 0)) }
    }

    @Test
    fun DistinctColours_CountsSampledColours() {
        assertEquals(2, FrameDiff.distinctColours(frame(1, 2, 3, 1, 2, 3, 4, 5, 6), step = 1))
        assertEquals(1, FrameDiff.distinctColours(frame(1, 2, 3, 4, 5, 6), step = 2))
    }
}
