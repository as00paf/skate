package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

@Serializable
data class AudioSettings(
    val masterVolume: Float = 1.0f,
    val musicVolume: Float = 0.8f,
    val sfxVolume: Float = 1.0f,
    val outputDevice: String = ""
) {
    fun validate(): AudioSettings {
        return copy(
            masterVolume = masterVolume.coerceIn(0f, 1f),
            musicVolume = musicVolume.coerceIn(0f, 1f),
            sfxVolume = sfxVolume.coerceIn(0f, 1f)
        )
    }
}
