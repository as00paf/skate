package com.pafoid.skate.engine.audio

import com.pafoid.skate.engine.assets.data.OpenAlSourceApi
import com.pafoid.skate.engine.assets.data.SourceApi
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11

/** The OpenAL listener and global distance/doppler calls; replaceable so the listener is testable without natives. */
interface ListenerApi {
    fun setPosition(x: Float, y: Float, z: Float)
    fun setVelocity(x: Float, y: Float, z: Float)
    /** [forward] then [up], 3 floats each. */
    fun setOrientation(forward: FloatArray, up: FloatArray)
    /** Turns OpenAL's own distance model off: attenuation is computed per source in [SpatialAudio]. */
    fun disableDistanceModel()
    fun setDopplerFactor(factor: Float)
    fun setSpeedOfSound(speed: Float)
}

object OpenAlListenerApi : ListenerApi {
    override fun setPosition(x: Float, y: Float, z: Float) = AL10.alListener3f(AL10.AL_POSITION, x, y, z)
    override fun setVelocity(x: Float, y: Float, z: Float) = AL10.alListener3f(AL10.AL_VELOCITY, x, y, z)
    override fun setOrientation(forward: FloatArray, up: FloatArray) = AL10.alListenerfv(AL10.AL_ORIENTATION, forward + up)
    override fun disableDistanceModel() = AL10.alDistanceModel(AL10.AL_NONE)
    override fun setDopplerFactor(factor: Float) = AL10.alDopplerFactor(factor)
    override fun setSpeedOfSound(speed: Float) = AL11.alSpeedOfSound(speed)
}

/**
 * Every OpenAL entry point the audio runtime uses ([AudioEngine] and, through it, `AudioSystem`). Pass fakes to
 * run the real engine and system without an audio device, e.g. in scene play tests.
 */
class AudioBackend(
    val alc: AlcApi = OpenAlcApi,
    val listener: ListenerApi = OpenAlListenerApi,
    val sources: SourceApi = OpenAlSourceApi,
    val efx: EffectsApi = OpenAlEfxApi,
)
