package com.pafoid.skate.editor.ui.windows

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConsoleAutoScrollTest {

    private val region = "Engine"

    @Test
    fun enabledByDefault_PinsToBottom() {
        val s = ConsoleAutoScroll()
        assertTrue(s.update(region, 0f, 0f))
        assertTrue(s.enabled)
    }

    @Test
    fun fastLogging_ContentGrowthNeverDisablesAndKeepsPinning() {
        val s = ConsoleAutoScroll()
        var y = 0f
        for (frame in 1..200) {
            val max = frame * 15f
            // Pinned last frame, so the offset sits at the previous maximum while the new maximum is larger.
            assertTrue(s.update(region, y, max), "frame $frame")
            y = max
        }
        assertTrue(s.enabled)
    }

    @Test
    fun scrollingUp_TurnsAutoScrollOffAndStopsPinning() {
        val s = ConsoleAutoScroll()
        s.update(region, 500f, 500f)
        assertFalse(s.update(region, 460f, 500f))
        assertFalse(s.enabled)
        // More logs arrive while the user reads: stays off, no pinning.
        assertFalse(s.update(region, 460f, 800f))
        assertFalse(s.enabled)
    }

    @Test
    fun scrollingBackToBottom_TurnsAutoScrollOn() {
        val s = ConsoleAutoScroll()
        s.update(region, 500f, 500f)
        s.update(region, 300f, 500f)
        assertFalse(s.update(region, 450f, 500f), "partway down stays off")
        assertTrue(s.update(region, 500f, 500f))
        assertTrue(s.enabled)
    }

    @Test
    fun scrollbarDragDownWithinTolerance_CountsAsBottom() {
        val s = ConsoleAutoScroll()
        s.update(region, 500f, 500f)
        s.update(region, 100f, 500f)
        assertTrue(s.update(region, 500f - ConsoleAutoScroll.BOTTOM_TOLERANCE, 500f))
    }

    @Test
    fun clampFromShrinkingContent_IsNotAUserScroll() {
        val s = ConsoleAutoScroll()
        s.update(region, 900f, 900f)
        // Logs cleared or filtered: ImGui clamps the offset to the new, smaller maximum.
        assertTrue(s.update(region, 120f, 120f))
        assertTrue(s.enabled)
    }

    @Test
    fun checkboxOff_AtBottom_StaysOffUntilUserScrollsDown() {
        val s = ConsoleAutoScroll()
        s.update(region, 500f, 500f)
        s.setEnabled(false)
        assertFalse(s.update(region, 500f, 500f), "sitting at the bottom does not re-enable")
        assertFalse(s.update(region, 500f, 700f), "new logs do not re-enable")
        assertTrue(s.update(region, 700f, 700f), "scrolling down to the bottom re-enables")
    }

    @Test
    fun checkboxOn_PinsImmediately() {
        val s = ConsoleAutoScroll(enabled = false)
        s.update(region, 100f, 500f)
        s.setEnabled(true)
        assertTrue(s.update(region, 100f, 500f))
    }

    @Test
    fun hold_SuppressesPinningWithoutChangingState() {
        val s = ConsoleAutoScroll()
        assertFalse(s.update(region, 500f, 600f, hold = true))
        assertTrue(s.enabled)
        assertTrue(s.update(region, 500f, 600f))
    }

    @Test
    fun regionsTrackOffsetsSeparately() {
        val s = ConsoleAutoScroll()
        s.update("Engine", 800f, 800f)
        // First frame of another tab at a lower offset is not a scroll-up.
        assertTrue(s.update("Editor", 50f, 50f))
        assertTrue(s.enabled)
    }
}
