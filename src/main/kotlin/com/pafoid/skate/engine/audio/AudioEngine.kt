package com.pafoid.skate.engine.audio

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.assets.data.SoundSource
import org.lwjgl.openal.AL
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import java.nio.IntBuffer

/** The ALC calls used by [AudioEngine]; replaceable so device lifetime is testable without natives. */
interface AlcApi {
    fun openDevice(): Long
    fun createContext(device: Long): Long
    fun makeCurrent(context: Long)
    fun currentContext(): Long
    fun createCapabilities(device: Long)
    fun destroyContext(context: Long)
    fun closeDevice(device: Long)
}

object OpenAlcApi : AlcApi {
    override fun openDevice() = ALC10.alcOpenDevice(null as CharSequence?)
    override fun createContext(device: Long) = ALC10.alcCreateContext(device, null as IntBuffer?)
    override fun makeCurrent(context: Long) {
        ALC10.alcMakeContextCurrent(context)
    }
    override fun currentContext() = ALC10.alcGetCurrentContext()
    override fun createCapabilities(device: Long) {
        AL.createCapabilities(ALC.createCapabilities(device))
    }
    override fun destroyContext(context: Long) = ALC10.alcDestroyContext(context)
    override fun closeDevice(device: Long) {
        ALC10.alcCloseDevice(device)
    }
}

/**
 * Owns the OpenAL device and context. Close it (or use `use {}`) to free them. Every AL call goes through [backend],
 * so tests can run the engine (and `AudioSystem`) on fakes.
 */
class AudioEngine(
    private val logger: LoggerService,
    val backend: AudioBackend = AudioBackend()
) : AutoCloseable {

    private val api: AlcApi get() = backend.alc
    val effects: EffectsManager = EffectsManager(backend.efx)

    private var device: Long = 0L
    private var context: Long = 0L
    var isInitialized = false
        private set

    val mixer = Mixer()
    val voices = VoiceManager<Int>()

    /** Applies volume/mute settings live; takes effect on the next AudioSystem update. */
    fun applyConfig(config: AudioConfig) = mixer.applyConfig(config)

    fun currentConfig(): AudioConfig = mixer.snapshot()

    fun init(): Boolean {
        if (isInitialized) return true

        try {
            device = api.openDevice()
            if (device == 0L) {
                logger.log("AudioEngine: Failed to open OpenAL device", LogLevel.ERROR)
                return false
            }

            context = api.createContext(device)
            if (context == 0L) {
                logger.log("AudioEngine: Failed to create OpenAL context", LogLevel.ERROR)
                releaseHandles()
                return false
            }

            api.makeCurrent(context)
            api.createCapabilities(device)

            isInitialized = true
            logger.log("AudioEngine: Initialized successfully")
            if (!effects.init()) logger.log("AudioEngine: EFX not supported, effects disabled", LogLevel.WARN)
            return true

        } catch (e: Exception) {
            logger.log("AudioEngine: Init failed - ${e.message}", LogLevel.ERROR)
            releaseHandles()
            return false
        }
    }

    /** Idempotent. Releases the context and device, including after a partially failed [init]. */
    override fun close() {
        if (!isInitialized && device == 0L && context == 0L) return
        voices.clear()
        runCatching { effects.close() }
        releaseHandles()
        logger.log("AudioEngine: Destroyed")
    }

    private fun releaseHandles() {
        try {
            if (context != 0L) {
                if (api.currentContext() == context) api.makeCurrent(0L)
                api.destroyContext(context)
            }
            if (device != 0L) api.closeDevice(device)
        } catch (e: Exception) {
            logger.log("AudioEngine: Cleanup failed - ${e.message}", LogLevel.ERROR)
        } finally {
            context = 0L
            device = 0L
            isInitialized = false
        }
    }

    fun getContext(): Long = context

    fun getDevice(): Long = device

    fun makeContextCurrent() {
        if (context != 0L) {
            api.makeCurrent(context)
        }
    }

    /** Re-binds this engine's context if another one (or none) is current on this thread. */
    fun ensureContextCurrent() {
        if (context != 0L && api.currentContext() != context) api.makeCurrent(context)
    }

    /** A new source on this engine's backend. */
    fun createSource(looping: Boolean, relative: Boolean): SoundSource = SoundSource(looping, relative, backend.sources)

    fun setListenerPosition(x: Float, y: Float, z: Float) {
        if (!isInitialized) return
        backend.listener.setPosition(x, y, z)
    }

    fun setListenerOrientation(forward: FloatArray, up: FloatArray) {
        if (!isInitialized) return
        backend.listener.setOrientation(forward, up)
    }

    fun setListenerVelocity(x: Float, y: Float, z: Float) {
        if (!isInitialized) return
        backend.listener.setVelocity(x, y, z)
    }

    /**
     * Distance attenuation and Doppler are computed per source in [SpatialAudio] (gain and pitch), so OpenAL's own
     * distance model and Doppler are disabled; otherwise the velocities sent to OpenAL would shift the pitch twice.
     */
    fun configureSpatial(speedOfSound: Float = SpatialAudio.DEFAULT_SPEED_OF_SOUND) {
        if (!isInitialized) return
        backend.listener.disableDistanceModel()
        backend.listener.setDopplerFactor(0f)
        backend.listener.setSpeedOfSound(speedOfSound)
    }

    /** Master gain is applied per source through the mixer; the AL listener gain stays at 1. */
    fun setMasterVolume(gain: Float) {
        mixer.bus(BusId.MASTER).volume = gain
    }
}
