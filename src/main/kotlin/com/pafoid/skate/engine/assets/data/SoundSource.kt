package com.pafoid.skate.engine.assets.data

import com.pafoid.skate.engine.audio.StreamSink
import org.lwjgl.openal.AL10.AL_BUFFER
import org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED
import org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED
import org.lwjgl.openal.AL10.AL_FALSE
import org.lwjgl.openal.AL10.AL_FORMAT_MONO16
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO16
import org.lwjgl.openal.AL10.AL_GAIN
import org.lwjgl.openal.AL10.AL_LOOPING
import org.lwjgl.openal.AL10.AL_PITCH
import org.lwjgl.openal.AL10.AL_PLAYING
import org.lwjgl.openal.AL10.AL_POSITION
import org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE
import org.lwjgl.openal.AL10.AL_SOURCE_STATE
import org.lwjgl.openal.AL10.AL_STOPPED
import org.lwjgl.openal.AL10.AL_TRUE
import org.lwjgl.openal.AL10.AL_VELOCITY
import org.lwjgl.openal.AL10.alBufferData
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.openal.AL10.alDeleteSources
import org.lwjgl.openal.AL10.alGenBuffers
import org.lwjgl.openal.AL10.alGenSources
import org.lwjgl.openal.AL10.alGetSourcei
import org.lwjgl.openal.AL10.alSource3f
import org.lwjgl.openal.AL10.alSourcePause
import org.lwjgl.openal.AL10.alSourcePlay
import org.lwjgl.openal.AL10.alSourceQueueBuffers
import org.lwjgl.openal.AL10.alSourceStop
import org.lwjgl.openal.AL10.alSourceUnqueueBuffers
import org.lwjgl.openal.AL10.alSourcef
import org.lwjgl.openal.AL10.alSourcei

/** The OpenAL source/buffer calls used by [SoundSource]; replaceable so lifetime logic is testable. */
interface SourceApi {
    fun genSource(): Int
    fun deleteSource(id: Int)
    fun sourceInt(id: Int, param: Int, value: Int)
    fun sourceFloat(id: Int, param: Int, value: Float)
    fun source3f(id: Int, param: Int, x: Float, y: Float, z: Float)
    fun getSourceInt(id: Int, param: Int): Int
    fun play(id: Int)
    fun pause(id: Int)
    fun stop(id: Int)
    fun genBuffer(): Int
    fun deleteBuffer(id: Int)
    fun bufferData(id: Int, stereo: Boolean, pcm: ShortArray, sampleRate: Int)
    fun queueBuffer(source: Int, buffer: Int)
    fun unqueueBuffer(source: Int): Int
}

object OpenAlSourceApi : SourceApi {
    override fun genSource() = alGenSources()
    override fun deleteSource(id: Int) = alDeleteSources(id)
    override fun sourceInt(id: Int, param: Int, value: Int) = alSourcei(id, param, value)
    override fun sourceFloat(id: Int, param: Int, value: Float) = alSourcef(id, param, value)
    override fun source3f(id: Int, param: Int, x: Float, y: Float, z: Float) = alSource3f(id, param, x, y, z)
    override fun getSourceInt(id: Int, param: Int) = alGetSourcei(id, param)
    override fun play(id: Int) = alSourcePlay(id)
    override fun pause(id: Int) = alSourcePause(id)
    override fun stop(id: Int) = alSourceStop(id)
    override fun genBuffer() = alGenBuffers()
    override fun deleteBuffer(id: Int) = alDeleteBuffers(id)
    override fun bufferData(id: Int, stereo: Boolean, pcm: ShortArray, sampleRate: Int) =
        alBufferData(id, if (stereo) AL_FORMAT_STEREO16 else AL_FORMAT_MONO16, pcm, sampleRate)
    override fun queueBuffer(source: Int, buffer: Int) = alSourceQueueBuffers(source, buffer)
    override fun unqueueBuffer(source: Int) = alSourceUnqueueBuffers(source)
}

/** One OpenAL source. [close] is idempotent; after it, every other call is a no-op. */
class SoundSource(
    isLooping: Boolean,
    isRelative: Boolean,
    private val api: SourceApi = OpenAlSourceApi
) : AutoCloseable {

    var sourceId: Int = -1
        private set

    val isClosed: Boolean get() = sourceId == -1

    init {
        sourceId = api.genSource()
        setLooping(isLooping)
        setRelative(isRelative)
        setVolume(1.0f)
    }

    fun setBuffer(bufferId: Int) {
        if (isClosed) return
        stop()
        api.sourceInt(sourceId, AL_BUFFER, bufferId)
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        if (!isClosed) api.source3f(sourceId, AL_POSITION, x, y, z)
    }

    fun setVelocity(x: Float, y: Float, z: Float) {
        if (!isClosed) api.source3f(sourceId, AL_VELOCITY, x, y, z)
    }

    fun setVolume(volume: Float) {
        if (!isClosed) api.sourceFloat(sourceId, AL_GAIN, volume)
    }

    fun setPitch(pitch: Float) {
        if (!isClosed) api.sourceFloat(sourceId, AL_PITCH, pitch.coerceIn(0.01f, 8f))
    }

    fun setLooping(loop: Boolean) {
        if (!isClosed) api.sourceInt(sourceId, AL_LOOPING, if (loop) AL_TRUE else AL_FALSE)
    }

    fun setRelative(relative: Boolean) {
        if (!isClosed) api.sourceInt(sourceId, AL_SOURCE_RELATIVE, if (relative) AL_TRUE else AL_FALSE)
    }

    fun play() {
        if (!isClosed && api.getSourceInt(sourceId, AL_SOURCE_STATE) != AL_PLAYING) api.play(sourceId)
    }

    fun stop() {
        if (!isClosed) api.stop(sourceId)
    }

    fun pause() {
        if (!isClosed && isPlaying()) api.pause(sourceId)
    }

    fun isPlaying(): Boolean = !isClosed && api.getSourceInt(sourceId, AL_SOURCE_STATE) == AL_PLAYING

    /**
     * Detaches any static buffer and returns a sink that streams PCM through this source.
     * The sink's buffers are owned by the caller (see StreamingPlayer.close).
     */
    fun asStreamSink(): StreamSink {
        stop()
        if (!isClosed) api.sourceInt(sourceId, AL_BUFFER, 0)
        return object : StreamSink {
            override fun createBuffers(count: Int) = IntArray(count) { api.genBuffer() }
            override fun deleteBuffers(ids: IntArray) = ids.forEach { api.deleteBuffer(it) }
            override fun upload(bufferId: Int, pcm: ShortArray, sampleCount: Int, channels: Int, sampleRate: Int) =
                api.bufferData(bufferId, channels != 1, pcm.copyOf(sampleCount), sampleRate)

            override fun queue(bufferId: Int) {
                if (!isClosed) api.queueBuffer(sourceId, bufferId)
            }

            override fun unqueueProcessed(): IntArray {
                if (isClosed) return IntArray(0)
                // After close/stop every queued buffer counts as processed.
                val n = api.getSourceInt(sourceId, AL_BUFFERS_PROCESSED)
                return IntArray(n) { api.unqueueBuffer(sourceId) }
            }

            override fun queuedCount() = if (isClosed) 0 else api.getSourceInt(sourceId, AL_BUFFERS_QUEUED)
            override fun isStopped() = !isClosed && api.getSourceInt(sourceId, AL_SOURCE_STATE) == AL_STOPPED
            override fun play() {
                if (!isClosed) api.play(sourceId)
            }
        }
    }

    override fun close() {
        if (isClosed) return
        val id = sourceId
        sourceId = -1
        api.stop(id)
        api.sourceInt(id, AL_BUFFER, 0)
        api.deleteSource(id)
    }
}
