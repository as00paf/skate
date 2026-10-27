package com.pafoid.skate.engine.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class VoiceManagerTest {
    private fun granted(r: VoiceManager.Result) = r is VoiceManager.Result.Granted<*>
    private fun stolen(r: VoiceManager.Result) = (r as VoiceManager.Result.Granted<*>).stolen

    @Test
    fun VoiceManager_GrantsUntilCapWithoutStealing() {
        val vm = VoiceManager<Int>(2)
        assertEquals(null, stolen(vm.request(1, 10, 1f)))
        assertEquals(null, stolen(vm.request(2, 10, 1f)))
        assertEquals(2, vm.activeCount)
    }

    @Test
    fun VoiceManager_StealsLowestPriorityVoice() {
        val vm = VoiceManager<Int>(2)
        vm.request(1, 200, 1f)
        vm.request(2, 50, 1f)
        assertEquals(2, stolen(vm.request(3, 100, 1f)))
        assertTrue(vm.isActive(1) && vm.isActive(3) && !vm.isActive(2))
    }

    @Test
    fun VoiceManager_DeniesWhenEveryVoiceOutranksRequest() {
        val vm = VoiceManager<Int>(1)
        vm.request(1, 200, 0.1f)
        assertEquals(VoiceManager.Result.Denied, vm.request(2, 100, 1f))
        assertTrue(vm.isActive(1))
    }

    @Test
    fun VoiceManager_EqualPriorityComparesAudibilityThenAge() {
        val vm = VoiceManager<Int>(2)
        vm.request(1, 100, 0.9f)
        vm.request(2, 100, 0.2f)
        assertEquals(VoiceManager.Result.Denied, vm.request(3, 100, 0.1f))
        assertEquals(2, stolen(vm.request(3, 100, 0.5f)))

        val ties = VoiceManager<Int>(2)
        ties.request(1, 100, 0.5f)
        ties.request(2, 100, 0.5f)
        assertEquals(1, stolen(ties.request(3, 100, 0.5f))) // oldest goes first
    }

    @Test
    fun VoiceManager_ReleaseFreesVoiceAndRerequestKeepsOwnerSingle() {
        val vm = VoiceManager<Int>(1)
        vm.request(1, 1, 1f)
        assertEquals(null, stolen(vm.request(1, 1, 1f))) // same owner re-requests: no steal
        assertEquals(1, vm.activeCount)
        vm.release(1)
        assertTrue(granted(vm.request(2, 1, 1f)))
        assertEquals(null, stolen(vm.request(2, 1, 1f)))
    }

    @Test
    fun VoiceManager_UpdatedAudibilityChangesVictim() {
        val vm = VoiceManager<Int>(2)
        vm.request(1, 100, 0.1f)
        vm.request(2, 100, 0.8f)
        vm.updateAudibility(1, 0.9f)
        vm.updateAudibility(2, 0.05f)
        assertEquals(2, stolen(vm.request(3, 100, 0.5f)))
    }

    @Test
    fun ClipPicker_RandomContainerNeverRepeatsImmediately() {
        val picker = ClipPicker(Random(1))
        val clips = listOf("a.wav", "b.wav", "c.wav")
        var prev = ""
        repeat(50) {
            val c = picker.pick(7, "", clips)
            assertTrue(c in clips)
            assertFalse(c == prev)
            prev = c
        }
    }

    @Test
    fun ClipPicker_FallsBackToSingleClip() {
        val picker = ClipPicker()
        assertEquals("one.wav", picker.pick(1, "one.wav", emptyList()))
        assertEquals("one.wav", picker.pick(1, "x.wav", listOf("", "one.wav")))
    }

    @Test
    fun StreamPolicy_StreamsLargeOrMusicOggOnly() {
        assertTrue(StreamPolicy.shouldStream("m.ogg", BusId.MUSIC, StreamMode.AUTO, 10))
        assertTrue(StreamPolicy.shouldStream("big.OGG", BusId.SFX, StreamMode.AUTO, 5_000_000))
        assertFalse(StreamPolicy.shouldStream("small.ogg", BusId.SFX, StreamMode.AUTO, 1000))
        assertFalse(StreamPolicy.shouldStream("m.wav", BusId.MUSIC, StreamMode.ALWAYS, 5_000_000))
        assertFalse(StreamPolicy.shouldStream("m.ogg", BusId.MUSIC, StreamMode.NEVER, 5_000_000))
        assertTrue(StreamPolicy.shouldStream("s.ogg", BusId.SFX, StreamMode.ALWAYS, 1))
    }
}
