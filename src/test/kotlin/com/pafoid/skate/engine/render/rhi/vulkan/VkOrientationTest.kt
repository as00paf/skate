package com.pafoid.skate.engine.render.rhi.vulkan

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** VK-05 texture-orientation convention: GL-ordered targets, top-first back buffer. */
class VkOrientationTest {
    @Test
    fun Viewport_TargetsAreNotFlipped() {
        assertArrayEquals(floatArrayOf(10f, 20f, 300f, 200f), VkOrientation.viewport(10, 20, 300, 200, 720, topFirst = false))
    }

    @Test
    fun Viewport_BackBufferIsFlipped() {
        // Same as the REN-13 flipped viewport: origin at the GL top edge, negative height
        assertArrayEquals(ClipSpace.flippedViewport(10, 20, 300, 200, 720), VkOrientation.viewport(10, 20, 300, 200, 720, topFirst = true))
        assertArrayEquals(floatArrayOf(10f, 700f, 300f, -200f), VkOrientation.viewport(10, 20, 300, 200, 720, topFirst = true))
    }

    @Test
    fun RectOffset_ScissorAndReadback() {
        assertArrayEquals(intArrayOf(5, 40), VkOrientation.rectOffset(5, 40, 100, 720, topFirst = false))
        assertArrayEquals(intArrayOf(5, 580), VkOrientation.rectOffset(5, 40, 100, 720, topFirst = true))
        // Matches the REN-13 scissor conversion for the back buffer
        assertEquals(ClipSpace.scissor(5, 40, 50, 100, 720)[1], VkOrientation.rectOffset(5, 40, 100, 720, topFirst = true)[1])
    }

    @Test
    fun FrontFace_ClockwiseInGlOrderedTargets() {
        assertFalse(VkOrientation.frontFaceCounterClockwise(topFirst = false))
        assertTrue(VkOrientation.frontFaceCounterClockwise(topFirst = true))
    }

    @Test
    fun BlitOffsets_TargetToTargetCopiesRowsAsTheyAre() {
        val o = VkOrientation.blitOffsets(0, 0, 640, 360, 360, false, 0, 0, 640, 360, 360, false)
        // GL top row y=360 maps to y=360, bottom row 0 to 0: no mirror
        assertArrayEquals(intArrayOf(0, 360, 640, 0, 0, 360, 640, 0), o)
    }

    @Test
    fun BlitOffsets_TargetToBackBufferMirrorsRows() {
        val o = VkOrientation.blitOffsets(0, 0, 640, 360, 360, false, 0, 0, 1280, 720, 720, true)
        // GL top row of the target (y=360) lands on the back buffer's first row (0)
        assertArrayEquals(intArrayOf(0, 360, 640, 0, 0, 0, 1280, 720), o)
    }

    @Test
    fun BlitOffsets_SubRects() {
        val o = VkOrientation.blitOffsets(10, 20, 100, 50, 200, false, 30, 40, 100, 50, 300, true)
        assertArrayEquals(intArrayOf(10, 70, 110, 20, 30, 210, 130, 260), o)
    }

    @Test
    fun ReadRow_GlOrderOut() {
        assertEquals(0, VkOrientation.readRow(0, 10, topFirst = false))
        assertEquals(9, VkOrientation.readRow(0, 10, topFirst = true))
        assertEquals(0, VkOrientation.readRow(9, 10, topFirst = true))
    }
}
