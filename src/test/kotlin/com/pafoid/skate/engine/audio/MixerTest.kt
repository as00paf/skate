package com.pafoid.skate.engine.audio

import com.pafoid.skate.editor.settings.AudioSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MixerTest {
    private val eps = 1e-5f

    @Test
    fun Mixer_SourceGainMultipliesOwnVolumeBusAndMaster() {
        val mixer = Mixer()
        mixer.applyConfig(AudioConfig(masterVolume = 0.5f, musicVolume = 0.8f))
        assertEquals(0.5f * 0.8f * 0.5f, mixer.sourceGain(BusId.MUSIC, 0.5f), eps)
    }

    @Test
    fun Mixer_MutedBusSilencesOnlyThatBus() {
        val mixer = Mixer()
        mixer.applyConfig(AudioConfig(sfxMuted = true))
        assertEquals(0f, mixer.sourceGain(BusId.SFX, 1f), eps)
        assertEquals(1f, mixer.sourceGain(BusId.UI, 1f), eps)
    }

    @Test
    fun Mixer_MutedMasterSilencesAllBuses() {
        val mixer = Mixer()
        mixer.applyConfig(AudioConfig(masterMuted = true))
        BusId.values().forEach { assertEquals(0f, mixer.sourceGain(it, 1f), eps) }
    }

    @Test
    fun Mixer_ClampsOutOfRangeAndNaNValues() {
        val mixer = Mixer()
        mixer.bus(BusId.SFX).volume = 3f
        assertEquals(1f, mixer.sourceGain(BusId.SFX, 2f), eps)
        mixer.bus(BusId.SFX).volume = Float.NaN
        assertEquals(0f, mixer.sourceGain(BusId.SFX, 1f), eps)
        assertEquals(0f, mixer.sourceGain(BusId.UI, -1f), eps)
    }

    @Test
    fun Mixer_ConfigChangesApplyLiveAndUnmuteRestoresVolume() {
        val mixer = Mixer()
        mixer.applyConfig(AudioConfig(sfxVolume = 0.4f, sfxMuted = true))
        mixer.applyConfig(AudioConfig(sfxVolume = 0.4f, sfxMuted = false))
        assertEquals(0.4f, mixer.sourceGain(BusId.SFX, 1f), eps)
    }

    @Test
    fun Mixer_AudioSettingsMapToConfigWithValidation() {
        val config = AudioSettings(masterVolume = 2f, musicVolume = 0.3f, uiVolume = 0.6f, uiMuted = true).toConfig()
        assertEquals(1f, config.masterVolume, eps)
        assertEquals(0.3f, config.musicVolume, eps)
        assertEquals(0.6f, config.uiVolume, eps)
        assertEquals(true, config.uiMuted)
    }
}
