package com.pafoid.skate.engine.render.rhi.vulkan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** VK-01 frame-in-flight bookkeeping, without a device: fences are simulated. */
class FrameRingTest {
    private val tracker = SubmissionTracker()
    private val waited = ArrayList<Int>()

    private fun ring(n: Int) = FrameRing(n, tracker) { waited += it }

    /** One frame the way VkFrameLoop runs it. */
    private fun FrameRing.frame(): Int {
        val slot = begin()
        tracker.recording = true
        submitFenced()
        end()
        return slot
    }

    @Test
    fun FrameRing_CyclesSlots() {
        val r = ring(2)
        assertEquals(listOf(0, 1, 0, 1, 0), List(5) { r.frame() })
        assertEquals(5L, r.frameCount)
    }

    @Test
    fun FrameRing_FirstUseOfEachSlotDoesNotWait() {
        val r = ring(3)
        repeat(3) { r.frame() }
        assertTrue(waited.isEmpty(), "unused fences are free")
    }

    @Test
    fun FrameRing_ReusingASlotWaitsForItsPreviousFrame() {
        val r = ring(2)
        r.frame() // slot 0, serial 1
        r.frame() // slot 1, serial 2
        r.begin() // slot 0 again
        assertEquals(listOf(0), waited)
        assertEquals(1L, tracker.completed, "waiting on slot 0's fence completes serial 1, not serial 2")
    }

    @Test
    fun FrameRing_NoWaitWhenTheGpuIsKnownIdle() {
        val r = ring(2)
        r.frame()
        r.frame()
        tracker.completeAll() // e.g. a readback waited for the queue
        r.begin()
        assertTrue(waited.isEmpty())
    }

    @Test
    fun FrameRing_AtMostFramesInFlightQueued() {
        val r = ring(2)
        repeat(10) {
            r.frame()
            assertTrue(tracker.submitted - tracker.completed <= 2, "frame $it: ${tracker.submitted} submitted, ${tracker.completed} done")
        }
    }

    @Test
    fun FrameRing_BeginTwiceKeepsTheOpenFrame() {
        val r = ring(2)
        assertEquals(0, r.begin())
        assertEquals(0, r.begin())
        assertTrue(r.isOpen)
    }

    @Test
    fun FrameRing_SubmitOrEndOutsideAFrameFails() {
        val r = ring(2)
        assertThrows<IllegalStateException> { r.submitFenced() }
        assertThrows<IllegalStateException> { r.end() }
    }

    @Test
    fun FrameRing_RejectsZeroFrames() {
        assertThrows<IllegalArgumentException> { FrameRing(0, tracker) {} }
    }

    @Test
    fun SubmissionTracker_ReleaseRunsAtOnceWhenNothingIsInFlight() {
        var ran = false
        tracker.release { ran = true }
        assertTrue(ran)
    }

    @Test
    fun SubmissionTracker_ReleaseWaitsForSubmittedWork() {
        val log = ArrayList<String>()
        tracker.submit()
        tracker.release { log += "a" }
        tracker.submit()
        tracker.release { log += "b" }
        assertEquals(2, tracker.pendingReleases)
        tracker.complete(1)
        assertEquals(listOf("a"), log)
        tracker.complete(2)
        assertEquals(listOf("a", "b"), log)
    }

    @Test
    fun SubmissionTracker_ReleaseWaitsForCommandsStillRecorded() {
        var ran = false
        tracker.recording = true
        tracker.release { ran = true } // a resource closed while the frame that uses it is recorded
        tracker.completeAll()
        assertFalse(ran, "the recorded frame has not even been submitted")
        tracker.submit()
        tracker.complete(1)
        assertTrue(ran)
    }

    @Test
    fun SubmissionTracker_DeferredReleaseAcrossFramesInFlight() {
        val r = ring(2)
        var freed = false
        r.begin()
        tracker.recording = true
        tracker.release { freed = true } // closed during frame 1 (serial 1)
        r.submitFenced()
        r.end()
        r.frame() // frame 2 in slot 1: nothing waited on yet
        assertFalse(freed)
        r.begin() // slot 0 again: waits for frame 1
        assertTrue(freed)
    }

    @Test
    fun SubmissionTracker_CompleteNeverPassesSubmitted() {
        tracker.submit()
        tracker.complete(5)
        assertEquals(1L, tracker.completed)
    }

    @Test
    fun SubmissionTracker_ReleaseAllRunsEverythingInOrder() {
        val log = ArrayList<Int>()
        tracker.submit()
        repeat(3) { i -> tracker.release { log += i } }
        tracker.releaseAll()
        assertEquals(listOf(0, 1, 2), log)
        assertEquals(0, tracker.pendingReleases)
    }

    @Test
    fun SubmissionTracker_SubmitEndsRecording() {
        tracker.recording = true
        assertEquals(1L, tracker.submit())
        assertFalse(tracker.recording)
    }
}
