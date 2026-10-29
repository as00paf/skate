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

class BusEffectsConfigTest {
    private val eps = 1e-5f

    @org.junit.jupiter.api.Test
    fun Mixer_ConfigAppliesBusEffectsLive() {
        val mixer = Mixer()
        mixer.applyConfig(AudioConfig(musicLowPass = 0.4f, masterLowPass = 0.5f, uiReverbSend = 0.7f, sfxReverbSend = 0f))
        assertEquals(0.2f, mixer.bus(BusId.MUSIC).effectiveLowPassGainHF, eps)
        assertEquals(0.7f, mixer.bus(BusId.UI).reverbSend, eps)
        assertEquals(0f, mixer.bus(BusId.SFX).reverbSend, eps)
    }

    @org.junit.jupiter.api.Test
    fun Mixer_DefaultConfigKeepsDefaultEffects() {
        val mixer = Mixer()
        mixer.bus(BusId.SFX).lowPassGainHF = 0.1f
        mixer.applyConfig(AudioConfig())
        assertEquals(1f, mixer.bus(BusId.SFX).lowPassGainHF, eps)
        assertEquals(1f, mixer.bus(BusId.SFX).reverbSend, eps)
        assertEquals(0f, mixer.bus(BusId.MUSIC).reverbSend, eps)
    }

    @org.junit.jupiter.api.Test
    fun Mixer_AudioSettingsEffectsAreValidatedAndMapped() {
        val config = com.pafoid.skate.editor.settings.AudioSettings(
            musicLowPass = -1f, sfxReverbSend = 3f, uiLowPass = 0.3f
        ).toConfig()
        assertEquals(0f, config.musicLowPass, eps)
        assertEquals(1f, config.sfxReverbSend, eps)
        assertEquals(0.3f, config.uiLowPass, eps)
    }
}
