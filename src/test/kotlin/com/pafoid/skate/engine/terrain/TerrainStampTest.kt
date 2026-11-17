package com.pafoid.skate.engine.terrain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-28 task 13: flatten, raise, lower, falloff, priority and the chunk overlap index. */
class TerrainStampTest {
    @Test
    fun Modes_FlattenRaiseLower() {
        val flatten = TerrainStamp(y = 5f, falloff = 0f)
        assertEquals(5f, flatten.apply(0f, 0f, 9f)); assertEquals(5f, flatten.apply(0f, 0f, 1f))
        val raise = TerrainStamp(y = 5f, mode = StampMode.RAISE_TO, falloff = 0f)
        assertEquals(9f, raise.apply(0f, 0f, 9f)); assertEquals(5f, raise.apply(0f, 0f, 1f))
        val lower = TerrainStamp(y = 5f, mode = StampMode.LOWER_TO, falloff = 0f)
        assertEquals(5f, lower.apply(0f, 0f, 9f)); assertEquals(1f, lower.apply(0f, 0f, 1f))
    }

    @Test
    fun Falloff_BlendsBackSmoothlyOutsideTheFootprint() {
        val s = TerrainStamp(width = 10f, depth = 10f, y = 0f, falloff = 4f)
        assertEquals(0f, s.apply(4.9f, 0f, 10f))
        assertEquals(5f, s.apply(7f, 0f, 10f), 1e-5f) // halfway through the falloff
        assertEquals(10f, s.apply(9.1f, 0f, 10f))
        val samples = (0..40).map { s.apply(5f + it * 0.1f, 0f, 10f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a - 1e-6f }, "monotonic back to the recipe")
    }

    @Test
    fun Footprints_RectRotatesWithYaw_CircleIsRound() {
        val rect = TerrainStamp(width = 20f, depth = 2f, yaw = 90f, falloff = 0f, y = 1f)
        assertEquals(1f, rect.apply(0f, 9f, 0f), "the long side turned onto Z")
        assertEquals(0f, rect.apply(9f, 0f, 0f))
        val circle = TerrainStamp(footprint = StampFootprint.CIRCLE, radius = 3f, falloff = 0f, y = 1f)
        assertEquals(1f, circle.apply(2f, 2f, 0f)); assertEquals(0f, circle.apply(2.5f, 2.5f, 0f))
    }

    @Test
    fun Priority_HigherWinsWhateverTheListOrder() {
        val high = TerrainStamp(y = 8f, falloff = 0f, priority = 5)
        val low = TerrainStamp(y = 2f, falloff = 0f, priority = 1)
        val index = StampIndex(listOf(high, low), 16f)
        val hits = index.overlapping(0f, 0f, 0f, 0f)
        assertEquals(listOf(low, high), hits)
        assertEquals(8f, index.apply(hits, 0f, 0f, 0f))
    }

    @Test
    fun OverlapIndex_OnlyReturnsStampsReachingTheArea() {
        val near = TerrainStamp(x = 5f, z = 5f, width = 4f, depth = 4f, falloff = 2f)
        val far = TerrainStamp(x = 200f, z = 200f)
        val index = StampIndex(listOf(near, far), 32f)
        assertEquals(listOf(near), index.overlapping(0f, 0f, 31f, 31f))
        assertEquals(listOf(far), index.overlapping(190f, 190f, 210f, 210f))
        assertTrue(index.overlapping(-500f, -500f, -400f, -400f).isEmpty())
    }

    @Test
    fun Stamps_FlattenChunkSamples_AndStayLocal() {
        val stamp = TerrainStamp(x = 0f, z = 0f, width = 8f, depth = 8f, y = 2f, falloff = 2f)
        val g = TerrainGenerator(TerrainPreset.ROLLING_HILLS.recipe(), listOf(stamp), TerrainLayout(128f, 1f, 32), 30f, 7L, "terrain/S")
        val plain = TerrainGenerator(TerrainPreset.ROLLING_HILLS.recipe(), emptyList(), TerrainLayout(128f, 1f, 32), 30f, 7L, "terrain/S")
        val centre = TerrainChunkBuilder.build(g, 2, 2) // corner at (0, 0)
        assertEquals(2f, centre.height(0, 0)); assertEquals(2f, centre.height(3, 3))
        val corner = TerrainChunkBuilder.build(g, 0, 0)
        assertEquals(TerrainChunkBuilder.build(plain, 0, 0).heights.toList(), corner.heights.toList(), "far chunks are untouched")
    }
}
