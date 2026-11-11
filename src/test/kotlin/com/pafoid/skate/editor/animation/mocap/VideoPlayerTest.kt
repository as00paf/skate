package com.pafoid.skate.editor.animation.mocap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-20: range maths and playback of the Video to Animation preview. */
class VideoPlayerTest {
    @Test
    fun Range_IsTheWholeVideoUntilMarked_AndItsLengthIsOutMinusIn() {
        val p = VideoPlayer(30f, 91)
        assertFalse(p.trimmed)
        assertEquals(3f, p.rangeSeconds, 1e-6f)
        p.seek(15); p.markIn()
        p.seek(45); p.markOut()
        assertTrue(p.trimmed)
        assertEquals(15, p.inFrame); assertEquals(45, p.outFrame)
        assertEquals(31, p.rangeFrames)
        assertEquals(1f, p.rangeSeconds, 1e-6f)
        p.clearRange()
        assertFalse(p.trimmed)
    }

    @Test
    fun Markers_NeverCross_AndStayInTheVideo() {
        val p = VideoPlayer(30f, 100)
        p.markOut(20); p.markIn(50)
        assertEquals(50, p.inFrame); assertEquals(50, p.outFrame, "an in marker after the out marker pulls it along")
        p.markOut(10)
        assertEquals(10, p.inFrame); assertEquals(10, p.outFrame)
        p.markIn(-5); p.markOut(500)
        assertEquals(0, p.inFrame); assertEquals(99, p.outFrame)
    }

    @Test
    fun Playback_StartsAtTheInMarker_AndLoopsInsideTheRange() {
        val p = VideoPlayer(10f, 100)
        p.markIn(20); p.markOut(29)
        p.togglePlay()
        assertEquals(20, p.frame, "play from outside the range starts at the in marker")
        p.tick(0.55f); assertEquals(25, p.frame)
        p.tick(0.6f); assertEquals(21, p.frame, "wraps back inside the range")
        p.step(1); assertFalse(p.playing, "stepping pauses"); assertEquals(22, p.frame)
        p.step(-100); assertEquals(0, p.frame, "stepping can leave the range, clamped to the video")
    }

    @Test
    fun RealFrameCount_ReplacesTheProbeEstimate() {
        val p = VideoPlayer(30f, 90)
        p.updateFrameCount(60)
        assertEquals(59, p.outFrame, "an unmarked range follows the real length")
        p.markIn(10); p.markOut(40)
        p.updateFrameCount(30)
        assertEquals(10, p.inFrame); assertEquals(29, p.outFrame)
        assertEquals(1, VideoPlayer(30f, 0).frameCount, "a probe without a count still gives one frame")
    }

    @Test
    fun PreviewFrame_IsTheLastImageAtOrBeforeTheFrame() {
        val p = VideoPlayer(30f, 10)
        val images = intArrayOf(0, 3, 6, 9)
        assertEquals(3, p.previewFrameFor(images, 5))
        assertEquals(9, p.previewFrameFor(images, 9))
        assertEquals(0, p.previewFrameFor(images, 0))
        assertEquals(4, p.previewFrameFor(intArrayOf(4, 8), 1), "before the first image: the first image")
        assertNull(p.previewFrameFor(IntArray(0), 3))
    }
}
