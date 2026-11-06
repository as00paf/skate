package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.audio.PcmStreamDecoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class SoundDurationsTest {
    @TempDir
    lateinit var dir: File

    private class FakeDecoder(override val channels: Int, override val sampleRate: Int, private var left: Int) : PcmStreamDecoder {
        override fun read(out: ShortArray): Int = minOf(out.size, left).also { left -= it }
        override fun rewind() = Unit
        override fun close() = Unit
    }

    @Test
    fun Count_StreamsToTheEnd() {
        // 2.5 s of 44.1 kHz stereo, longer than one read chunk
        assertEquals(2.5f, SoundDurations.count(FakeDecoder(2, 44_100, 2 * 44_100 * 5 / 2)), 1e-4f)
    }

    @Test
    fun Count_EmptyStreamIsZero() {
        assertEquals(0f, SoundDurations.count(FakeDecoder(1, 22_050, 0)))
    }

    @Test
    fun Wav_ReadsTheHeader() {
        val format = AudioFormat(8_000f, 16, 1, true, false)
        val frames = 4_000L
        val file = File(dir, "half.wav")
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(ByteArray((frames * 2).toInt())), format, frames), AudioFileFormat.Type.WAVE, file)

        assertEquals(0.5f, SoundDurations.of(file), 1e-4f)
    }
}
