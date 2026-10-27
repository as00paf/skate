package com.pafoid.skate.engine.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamingPlayerTest {

    private class FakeDecoder(private var remainingChunks: Int, private val total: Int = remainingChunks) : PcmStreamDecoder {
        override val channels = 2
        override val sampleRate = 44100
        var maxRequest = 0
        var rewinds = 0
        var closed = false
        override fun read(out: ShortArray): Int {
            maxRequest = maxOf(maxRequest, out.size)
            if (remainingChunks == 0) return 0
            remainingChunks--
            return out.size
        }
        override fun rewind() {
            rewinds++
            remainingChunks = total
        }
        override fun close() {
            closed = true
        }
    }

    private class FakeSink : StreamSink {
        val queued = ArrayDeque<Int>()
        val processed = ArrayDeque<Int>()
        var plays = 0
        var stopped = false
        var deleted = false
        var uploads = 0
        override fun createBuffers(count: Int) = IntArray(count) { it + 1 }
        override fun deleteBuffers(ids: IntArray) { deleted = true }
        override fun upload(bufferId: Int, pcm: ShortArray, sampleCount: Int, channels: Int, sampleRate: Int) { uploads++ }
        override fun queue(bufferId: Int) { queued.addLast(bufferId) }
        override fun unqueueProcessed(): IntArray {
            val ids = processed.toList().toIntArray()
            processed.clear()
            ids.forEach { queued.remove(it) }
            return ids
        }
        override fun queuedCount() = queued.size
        override fun isStopped() = stopped
        override fun play() { plays++; stopped = false }
        fun consume(n: Int) = repeat(n) { queued.firstOrNull { it !in processed }?.let(processed::addLast) }
    }

    @Test
    fun StreamingPlayer_OnlyBoundedChunksAreDecodedAtOnce() {
        val decoder = FakeDecoder(1000)
        val sink = FakeSink()
        val player = StreamingPlayer(decoder, sink, bufferCount = 4, chunkSamples = 1024)
        player.start()
        assertEquals(4, sink.uploads) // never the whole stream
        assertEquals(1024, decoder.maxRequest)
        assertEquals(1, sink.plays)
        assertEquals(4, sink.queuedCount())
    }

    @Test
    fun StreamingPlayer_RefillsProcessedBuffersAndFinishesAtEnd() {
        val decoder = FakeDecoder(5)
        val sink = FakeSink()
        val player = StreamingPlayer(decoder, sink, bufferCount = 4, chunkSamples = 1024)
        player.start()
        sink.consume(2)
        player.update()
        assertEquals(5, sink.uploads)
        assertEquals(3, sink.queuedCount()) // 4 - 2 consumed + 1 refilled; the second refill hit EOF
        assertFalse(player.isFinished)
        sink.consume(3)
        player.update()
        assertTrue(player.isFinished)
    }

    @Test
    fun StreamingPlayer_LoopsByRewindingInsteadOfFinishing() {
        val decoder = FakeDecoder(2)
        val sink = FakeSink()
        val player = StreamingPlayer(decoder, sink, looping = true, bufferCount = 4, chunkSamples = 1024)
        player.start()
        assertEquals(4, sink.uploads)
        assertEquals(1, decoder.rewinds)
        assertFalse(player.isFinished)
    }

    @Test
    fun StreamingPlayer_RestartsAfterUnderrunUnlessPaused() {
        val sink = FakeSink()
        val player = StreamingPlayer(FakeDecoder(100), sink, bufferCount = 2, chunkSamples = 1024)
        player.start()
        sink.stopped = true
        player.paused = true
        player.update()
        assertEquals(1, sink.plays)
        player.paused = false
        player.update()
        assertEquals(2, sink.plays)
    }

    @Test
    fun StreamingPlayer_CloseReleasesBuffersAndDecoder() {
        val decoder = FakeDecoder(3)
        val sink = FakeSink()
        val player = StreamingPlayer(decoder, sink)
        player.close()
        assertTrue(sink.deleted && decoder.closed)
    }
}
