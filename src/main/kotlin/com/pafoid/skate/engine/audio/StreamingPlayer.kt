package com.pafoid.skate.engine.audio

import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.IOException

/** Incremental PCM (16-bit interleaved) source. */
interface PcmStreamDecoder : AutoCloseable {
    val channels: Int
    val sampleRate: Int

    /** Fills [out] and returns the number of samples written (frames x channels); 0 at end of stream. */
    fun read(out: ShortArray): Int
    fun rewind()
}

/** The queue side of an audio source, abstracted from OpenAL for testing. */
interface StreamSink {
    fun createBuffers(count: Int): IntArray
    fun deleteBuffers(ids: IntArray)
    fun upload(bufferId: Int, pcm: ShortArray, sampleCount: Int, channels: Int, sampleRate: Int)
    fun queue(bufferId: Int)

    /** Removes and returns the buffers the source has finished playing. */
    fun unqueueProcessed(): IntArray
    fun queuedCount(): Int
    fun isStopped(): Boolean
    fun play()
}

/**
 * Keeps a small ring of buffers queued on a source so long music is never fully decoded:
 * memory use is bounded by `bufferCount * chunkSamples`.
 */
class StreamingPlayer(
    private val decoder: PcmStreamDecoder,
    private val sink: StreamSink,
    var looping: Boolean = false,
    bufferCount: Int = 4,
    chunkSamples: Int = 32768
) : AutoCloseable {
    private val chunk = ShortArray(chunkSamples - chunkSamples % decoder.channels)
    private val bufferIds = sink.createBuffers(bufferCount)
    private var endOfStream = false
    private var closed = false

    var paused = false

    val isFinished: Boolean get() = endOfStream && sink.queuedCount() == 0

    fun start() {
        bufferIds.forEach { fill(it) }
        sink.play()
    }

    /** Refills processed buffers, and restarts the source after an underrun. */
    fun update() {
        if (closed) return
        sink.unqueueProcessed().forEach { fill(it) }
        if (!paused && sink.queuedCount() > 0 && sink.isStopped()) sink.play()
    }

    private fun fill(bufferId: Int) {
        var n = if (endOfStream) 0 else decoder.read(chunk)
        if (n == 0 && !endOfStream && looping) {
            decoder.rewind()
            n = decoder.read(chunk)
        }
        if (n == 0) {
            endOfStream = true
            return
        }
        sink.upload(bufferId, chunk, n, decoder.channels, decoder.sampleRate)
        sink.queue(bufferId)
    }

    override fun close() {
        if (closed) return
        closed = true
        sink.unqueueProcessed()
        sink.deleteBuffers(bufferIds)
        decoder.close()
    }
}

class StbVorbisDecoder(path: String) : PcmStreamDecoder {
    private val handle: Long
    override val channels: Int
    override val sampleRate: Int

    init {
        MemoryStack.stackPush().use { stack ->
            val error = stack.mallocInt(1)
            handle = STBVorbis.stb_vorbis_open_filename(path, error, null)
            if (handle == 0L) throw IOException("stb_vorbis failed to open '$path' (error ${error.get(0)})")
            val info = STBVorbisInfo.malloc(stack)
            STBVorbis.stb_vorbis_get_info(handle, info)
            channels = info.channels()
            sampleRate = info.sample_rate()
        }
    }

    override fun read(out: ShortArray): Int {
        val buffer = MemoryUtil.memAllocShort(out.size)
        try {
            val frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, buffer)
            val samples = frames * channels
            buffer.get(out, 0, samples)
            return samples
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    override fun rewind() {
        STBVorbis.stb_vorbis_seek_start(handle)
    }

    override fun close() = STBVorbis.stb_vorbis_close(handle)
}
