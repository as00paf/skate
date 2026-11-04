package com.pafoid.skate.engine.audio

import com.pafoid.skate.engine.assets.data.SourceApi
import org.lwjgl.openal.AL10.AL_BUFFER
import org.lwjgl.openal.AL10.AL_GAIN
import org.lwjgl.openal.AL10.AL_INITIAL
import org.lwjgl.openal.AL10.AL_LOOPING
import org.lwjgl.openal.AL10.AL_PAUSED
import org.lwjgl.openal.AL10.AL_PITCH
import org.lwjgl.openal.AL10.AL_PLAYING
import org.lwjgl.openal.AL10.AL_POSITION
import org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE
import org.lwjgl.openal.AL10.AL_SOURCE_STATE
import org.lwjgl.openal.AL10.AL_STOPPED
import org.lwjgl.openal.AL10.AL_TRUE
import org.joml.Vector3f

/**
 * In-memory OpenAL for tests: `AudioEngine(logger, FakeAudioBackend().backend)` runs the real engine and
 * `AudioSystem` without natives, and records what a device would hear (live sources, gains, listener).
 * Reading the AL constants is fine without natives (they are compile-time ints).
 */
class FakeAudioBackend {

    class Source(val id: Int) {
        var state = AL_INITIAL
        var gain = 1f
        var pitch = 1f
        var looping = false
        var relative = false
        var buffer = 0
        val position = Vector3f()
        val isPlaying get() = state == AL_PLAYING
    }

    /** Live (not yet deleted) sources by id. */
    val sources = linkedMapOf<Int, Source>()
    val listenerPosition = Vector3f()
    val listenerForward = Vector3f(0f, 0f, -1f)
    var distanceModelDisabled = false

    val playing: List<Source> get() = sources.values.filter { it.isPlaying }

    private var nextSource = 1
    private var nextBuffer = 1000

    val alc = object : AlcApi {
        var current = 0L
        override fun openDevice() = 1L
        override fun createContext(device: Long) = 2L
        override fun makeCurrent(context: Long) { current = context }
        override fun currentContext() = current
        override fun createCapabilities(device: Long) {}
        override fun destroyContext(context: Long) {}
        override fun closeDevice(device: Long) {}
    }

    val listener = object : ListenerApi {
        override fun setPosition(x: Float, y: Float, z: Float) { listenerPosition.set(x, y, z) }
        override fun setVelocity(x: Float, y: Float, z: Float) {}
        override fun setOrientation(forward: FloatArray, up: FloatArray) { listenerForward.set(forward[0], forward[1], forward[2]) }
        override fun disableDistanceModel() { distanceModelDisabled = true }
        override fun setDopplerFactor(factor: Float) {}
        override fun setSpeedOfSound(speed: Float) {}
    }

    val sourceApi = object : SourceApi {
        override fun genSource() = nextSource++.also { sources[it] = Source(it) }
        override fun deleteSource(id: Int) { sources.remove(id) }
        override fun sourceInt(id: Int, param: Int, value: Int) {
            val s = sources[id] ?: return
            when (param) {
                AL_LOOPING -> s.looping = value == AL_TRUE
                AL_SOURCE_RELATIVE -> s.relative = value == AL_TRUE
                AL_BUFFER -> s.buffer = value
            }
        }
        override fun sourceFloat(id: Int, param: Int, value: Float) {
            val s = sources[id] ?: return
            when (param) {
                AL_GAIN -> s.gain = value
                AL_PITCH -> s.pitch = value
            }
        }
        override fun source3f(id: Int, param: Int, x: Float, y: Float, z: Float) {
            if (param == AL_POSITION) sources[id]?.position?.set(x, y, z)
        }
        override fun getSourceInt(id: Int, param: Int) =
            if (param == AL_SOURCE_STATE) sources[id]?.state ?: AL_STOPPED else 0
        override fun play(id: Int) { sources[id]?.state = AL_PLAYING }
        override fun pause(id: Int) { sources[id]?.state = AL_PAUSED }
        override fun stop(id: Int) { sources[id]?.state = AL_STOPPED }
        override fun genBuffer() = nextBuffer++
        override fun deleteBuffer(id: Int) {}
        override fun bufferData(id: Int, stereo: Boolean, pcm: ShortArray, sampleRate: Int) {}
        override fun queueBuffer(source: Int, buffer: Int) {}
        override fun unqueueBuffer(source: Int) = 0
    }

    val efx = object : EffectsApi {
        override fun isSupported() = false
        override fun createSlot() = 0
        override fun createReverbEffect() = 0
        override fun setReverbParams(effect: Int, params: ReverbParams) {}
        override fun attachEffect(slot: Int, effect: Int) {}
        override fun createFilter() = 0
        override fun setLowPass(filter: Int, gain: Float, gainHF: Float) {}
        override fun setDirectFilter(source: Int, filter: Int) {}
        override fun setSend(source: Int, slot: Int, filter: Int) {}
        override fun deleteSlot(slot: Int) {}
        override fun deleteEffect(effect: Int) {}
        override fun deleteFilter(filter: Int) {}
    }

    val backend = AudioBackend(alc = alc, listener = listener, sources = sourceApi, efx = efx)
}
