package com.pafoid.skate.engine.audio

import com.pafoid.skate.engine.assets.data.SoundSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.IOException
import kotlin.math.abs

/** Fixtures: src/test/resources/audio (synthetic CC0 tones, see the README there). */
class Mp3DecoderTest {

    private fun fixture(name: String) = File(javaClass.getResource("/audio/$name")!!.toURI())

    private fun peak(pcm: ShortArray, channel: Int, channels: Int) =
        pcm.indices.filter { it % channels == channel }.maxOf { abs(pcm[it].toInt()) }

    @Test
    fun Mp3_StereoWithId3TagDecodesBothChannels() {
        Mp3Decoder(fixture("tone-stereo-44k.mp3")).use { mp3 ->
            assertEquals(2, mp3.channels)
            assertEquals(44100, mp3.sampleRate)
            val pcm = mp3.readAll()
            val seconds = pcm.size / 2f / 44100
            assertTrue(seconds in 0.24f..0.32f, "duration $seconds")
            // Written at amplitude 8000 per channel.
            assertTrue(peak(pcm, 0, 2) in 6000..10000, "left peak ${peak(pcm, 0, 2)}")
            assertTrue(peak(pcm, 1, 2) in 6000..10000, "right peak ${peak(pcm, 1, 2)}")
        }
    }

    @Test
    fun Mp3_MonoDecodes() {
        Mp3Decoder(fixture("tone-mono-44k.mp3")).use { mp3 ->
            assertEquals(1, mp3.channels)
            assertTrue(peak(mp3.readAll(), 0, 1) in 6000..10000)
        }
    }

    @Test
    fun Mp3_ReadsWholeFramesAndRewindsToTheStart() {
        Mp3Decoder(fixture("tone-stereo-44k.mp3")).use { mp3 ->
            val chunk = ShortArray(1001)
            val first = mp3.read(chunk)
            assertEquals(1000, first)
            val head = chunk.copyOf(first)
            while (mp3.read(chunk) > 0) Unit
            mp3.rewind()
            assertEquals(1000, mp3.read(chunk))
            assertTrue(head.contentEquals(chunk.copyOf(1000)))
        }
    }

    @Test
    fun Mp3_Mpeg2FileFailsWithIOException() {
        val e = assertThrows<IOException> { Mp3Decoder(fixture("tone-mono-22k-mpeg2.mp3")) }
        assertTrue(e.message!!.contains("MPEG-1"))
    }

    @Test
    fun PcmDecoders_OpensMp3ByExtensionCaseInsensitively() {
        val dir = kotlin.io.path.createTempDirectory("mp3").toFile()
        try {
            val upper = File(dir, "My Track.MP3").also { fixture("tone-mono-44k.mp3").copyTo(it) }
            PcmDecoders.open(upper.path).use { assertTrue(it is Mp3Decoder) }
            assertThrows<IOException> { PcmDecoders.open(File(dir, "a.wav").path) }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun Mp3_StreamsThroughALoopingStreamingPlayer() {
        val al = FakeAudioBackend()
        val source = SoundSource(false, true, al.sourceApi)
        val player = StreamingPlayer(Mp3Decoder(fixture("tone-stereo-44k.mp3")), source.asStreamSink(), looping = true,
            bufferCount = 3, chunkSamples = 4096)
        player.start()
        assertTrue(al.sources.getValue(source.sourceId).isPlaying)
        repeat(10) {
            al.playQueued(source.sourceId)
            player.update()
        }
        // 13 chunks of 2048 stereo frames is more than the 0.25 s track: it looped instead of ending.
        val streamed = al.uploads.sumOf { it.samples } / 2f / 44100
        assertTrue(streamed > 0.5f, "streamed $streamed s")
        assertTrue(al.uploads.all { it.stereo && it.sampleRate == 44100 && it.samples % 2 == 0 })
        assertEquals(3, al.sources.getValue(source.sourceId).queued.size)
        player.close()
        source.close()
    }
}
