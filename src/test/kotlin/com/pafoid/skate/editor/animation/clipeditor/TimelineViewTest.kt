package com.pafoid.skate.editor.animation.clipeditor

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineViewTest {
    @Test
    fun TimeAndPixels_RoundTrip_AndZoomKeepsTheAnchor() {
        val v = TimelineView(pxPerSecond = 100f, scroll = 1f)
        assertEquals(150f, v.timeToX(2f, 50f))
        assertEquals(2f, v.xToTime(150f, 50f))
        v.zoom(2f, anchorX = 150f, originX = 50f)
        assertEquals(200f, v.pxPerSecond)
        assertEquals(2f, v.xToTime(150f, 50f), 1e-5f, "the time under the mouse stays put")
        v.fit(4f, 400f)
        assertEquals(0f, v.scroll)
        assertTrue(v.timeToX(4f, 0f) <= 400f)
    }

    @Test
    fun RulerLabels_UseNiceFrameSteps() {
        val v = TimelineView(pxPerSecond = 300f)
        assertEquals(listOf(0, 10, 20, 30), v.rulerFrames(30f, 300f, minLabelPx = 60f).take(4), "10 px per frame -> every 10 frames")
        v.pxPerSecond = 3000f
        assertEquals(listOf(0, 1, 2), v.rulerFrames(30f, 300f, minLabelPx = 60f).take(3), "100 px per frame -> every frame")
        v.pxPerSecond = 30f
        assertEquals(listOf(0, 100, 200), v.rulerFrames(30f, 300f, minLabelPx = 60f).take(3), "1 px per frame -> every 100 frames (50 is too tight)")
    }

    @Test
    fun Preview_ProjectsAndPicksJoints() {
        val p = SkeletonPreview()
        val points = arrayOf(Vector3f(0f, 1f, 0f), Vector3f(0f, 2f, 0f))
        p.frame(points)
        val screen = p.project(points, 200f, 200f)
        assertEquals(100f, screen[0]!![0], 1f, "the framed centre is in the middle")
        assertTrue(screen[1]!![1] < screen[0]!![1], "higher joints are higher on screen")
        assertEquals(1, p.pick(screen, screen[1]!![0] + 3f, screen[1]!![1]))
        assertEquals(-1, p.pick(screen, 0f, 0f))
        p.pitch = 0f; p.yaw = 0f
        assertNull(p.project(arrayOf(Vector3f(p.eye()).add(0f, 0f, 1f)), 200f, 200f)[0], "behind the camera")
    }
}
