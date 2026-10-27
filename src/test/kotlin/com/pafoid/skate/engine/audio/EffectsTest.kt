package com.pafoid.skate.engine.audio

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EffectsTest {

    private class FakeEfx(var supported: Boolean = true) : EffectsApi {
        val calls = mutableListOf<String>()
        var next = 1
        override fun isSupported() = supported
        override fun createSlot() = next++.also { calls += "slot" }
        override fun createReverbEffect() = next++.also { calls += "effect" }
        override fun setReverbParams(effect: Int, params: ReverbParams) { calls += "reverb:${params.decayTime}" }
        override fun attachEffect(slot: Int, effect: Int) { calls += "attach:$effect" }
        override fun createFilter() = next++.also { calls += "filter" }
        override fun setLowPass(filter: Int, gain: Float, gainHF: Float) { calls += "lp:$gain:$gainHF" }
        override fun setDirectFilter(source: Int, filter: Int) { calls += "direct:$source:$filter" }
        override fun setSend(source: Int, slot: Int, filter: Int) { calls += "send:$source:${if (slot == 0) 0 else 1}" }
        override fun deleteSlot(slot: Int) { calls += "delSlot" }
        override fun deleteEffect(effect: Int) { calls += "delEffect" }
        override fun deleteFilter(filter: Int) { calls += "delFilter" }
    }

    private fun zone(id: Int, x: Float, r: Float, prio: Int = 0) =
        ZoneShape(id, Vector3f(x, 0f, 0f), r, prio, ReverbPreset.HALL.params, 1f, 1f)

    @Test
    fun ReverbZone_ResolvesOnlyZonesContainingListener() {
        val zones = listOf(zone(1, 0f, 5f), zone(2, 100f, 5f))
        assertEquals(1, ReverbZoneResolver.resolve(Vector3f(1f, 0f, 0f), zones)?.id)
        assertNull(ReverbZoneResolver.resolve(Vector3f(50f, 0f, 0f), zones))
    }

    @Test
    fun ReverbZone_OverlapPicksHigherPriorityThenNearestCentre() {
        val l = Vector3f(0f, 0f, 0f)
        assertEquals(2, ReverbZoneResolver.resolve(l, listOf(zone(1, 1f, 10f, 0), zone(2, 5f, 10f, 3)))?.id)
        assertEquals(1, ReverbZoneResolver.resolve(l, listOf(zone(1, 1f, 10f), zone(2, 5f, 10f)))?.id)
    }

    @Test
    fun Effects_UnsupportedBackendIsANoOp() {
        val api = FakeEfx(supported = false)
        val fx = EffectsManager(api)
        assertFalse(fx.init())
        fx.setReverb(ReverbPreset.CAVE.params)
        fx.applySource(1, 0.5f, 1f)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun Effects_ReverbOnlyReappliedWhenItChanges() {
        val api = FakeEfx()
        val fx = EffectsManager(api).also { it.init() }
        fx.setReverb(ReverbPreset.CAVE.params)
        fx.setReverb(ReverbPreset.CAVE.params)
        assertEquals(1, api.calls.count { it.startsWith("reverb:") })
        fx.setReverb(null)
        assertEquals("attach:0", api.calls.last())
    }

    @Test
    fun Effects_SendIsDisabledWithoutReverbAndEnabledInsideZone() {
        val api = FakeEfx()
        val fx = EffectsManager(api).also { it.init() }
        fx.applySource(9, 1f, 1f)
        assertTrue(api.calls.contains("send:9:0"))
        assertFalse(api.calls.contains("filter"))
        fx.setReverb(ReverbPreset.HALL.params)
        fx.applySource(9, 1f, 0.5f)
        assertTrue(api.calls.contains("send:9:1"))
        assertTrue(api.calls.contains("lp:0.5:1.0"))
    }

    @Test
    fun Effects_LowPassCreatesFilterOnceAndSkipsUnchangedValues() {
        val api = FakeEfx()
        val fx = EffectsManager(api).also { it.init() }
        fx.applySource(3, 0.3f, 0f)
        val n = api.calls.size
        fx.applySource(3, 0.3f, 0f)
        assertEquals(n, api.calls.size)
        fx.applySource(3, 0.6f, 0f)
        assertEquals(1, api.calls.count { it == "filter" })
        fx.applySource(3, 1f, 0f)
        assertEquals("direct:3:0", api.calls.last())
    }

    @Test
    fun Effects_ReleaseAndCloseDeleteEveryNativeObjectOnce() {
        val api = FakeEfx()
        val fx = EffectsManager(api).also { it.init() }
        fx.setReverb(ReverbPreset.ROOM.params)
        fx.applySource(1, 0.5f, 1f) // direct + send filters
        fx.applySource(2, 0.5f, 0f) // direct filter
        fx.release(1)
        assertEquals(2, api.calls.count { it == "delFilter" })
        fx.release(1)
        assertEquals(2, api.calls.count { it == "delFilter" })
        fx.close()
        fx.close()
        assertEquals(3, api.calls.count { it == "delFilter" })
        assertEquals(1, api.calls.count { it == "delSlot" })
        assertEquals(1, api.calls.count { it == "delEffect" })
    }

    @Test
    fun Mixer_BusLowPassChainsThroughMasterAndClamps() {
        val mixer = Mixer()
        mixer.bus(BusId.MASTER).lowPassGainHF = 0.5f
        mixer.bus(BusId.MUSIC).lowPassGainHF = 0.5f
        assertEquals(0.25f, mixer.bus(BusId.MUSIC).effectiveLowPassGainHF, 1e-5f)
        mixer.bus(BusId.SFX).lowPassGainHF = 7f
        assertEquals(0.5f, mixer.bus(BusId.SFX).effectiveLowPassGainHF, 1e-5f)
        assertEquals(1f, mixer.bus(BusId.SFX).reverbSend, 0f)
        assertEquals(0f, mixer.bus(BusId.MUSIC).reverbSend, 0f)
    }
}
