package com.pafoid.skate.editor.settings

import com.pafoid.skate.engine.audio.AudioConfig
import kotlinx.serialization.Serializable

@Serializable
data class AudioSettings(
    val masterVolume: Float = 1.0f,
    val musicVolume: Float = 0.8f,
    val sfxVolume: Float = 1.0f,
    val uiVolume: Float = 1.0f,
    val masterMuted: Boolean = false,
    val musicMuted: Boolean = false,
    val sfxMuted: Boolean = false,
    val uiMuted: Boolean = false,
    val masterLowPass: Float = 1f,
    val musicLowPass: Float = 1f,
    val sfxLowPass: Float = 1f,
    val uiLowPass: Float = 1f,
    val musicReverbSend: Float = 0f,
    val sfxReverbSend: Float = 1f,
    val uiReverbSend: Float = 0f,
    val outputDevice: String = ""
) {
    fun validate(): AudioSettings {
        return copy(
            masterVolume = masterVolume.coerceIn(0f, 1f),
            musicVolume = musicVolume.coerceIn(0f, 1f),
            sfxVolume = sfxVolume.coerceIn(0f, 1f),
            uiVolume = uiVolume.coerceIn(0f, 1f),
            masterLowPass = masterLowPass.coerceIn(0f, 1f),
            musicLowPass = musicLowPass.coerceIn(0f, 1f),
            sfxLowPass = sfxLowPass.coerceIn(0f, 1f),
            uiLowPass = uiLowPass.coerceIn(0f, 1f),
            musicReverbSend = musicReverbSend.coerceIn(0f, 1f),
            sfxReverbSend = sfxReverbSend.coerceIn(0f, 1f),
            uiReverbSend = uiReverbSend.coerceIn(0f, 1f)
        )
    }

    fun toConfig(): AudioConfig = validate().let {
        AudioConfig(
            masterVolume = it.masterVolume,
            musicVolume = it.musicVolume,
            sfxVolume = it.sfxVolume,
            uiVolume = it.uiVolume,
            masterMuted = it.masterMuted,
            musicMuted = it.musicMuted,
            sfxMuted = it.sfxMuted,
            uiMuted = it.uiMuted,
            masterLowPass = it.masterLowPass,
            musicLowPass = it.musicLowPass,
            sfxLowPass = it.sfxLowPass,
            uiLowPass = it.uiLowPass,
            musicReverbSend = it.musicReverbSend,
            sfxReverbSend = it.sfxReverbSend,
            uiReverbSend = it.uiReverbSend
        )
    }
}
