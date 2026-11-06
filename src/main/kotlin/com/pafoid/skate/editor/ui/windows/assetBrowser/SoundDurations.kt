package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.audio.PcmDecoders
import com.pafoid.skate.engine.audio.PcmStreamDecoder
import java.io.File
import javax.sound.sampled.AudioSystem

/** Sound lengths for the Sounds tab, computed on an IO worker without creating OpenAL buffers (AST-23). */
object SoundDurations {

    /** Seconds of audio in [file]: WAV from its header, OGG/MP3 by streaming through the decoder (no full PCM copy). */
    fun of(file: File): Float = when (file.extension.lowercase()) {
        "wav" -> AudioSystem.getAudioFileFormat(file).let { it.frameLength / it.format.frameRate }
        else -> PcmDecoders.open(file.absolutePath).use(::count)
    }

    /** Streams [decoder] to its end and returns its length in seconds. */
    fun count(decoder: PcmStreamDecoder): Float {
        val chunk = ShortArray(16_384)
        var samples = 0L
        while (true) {
            val n = decoder.read(chunk)
            if (n <= 0) break
            samples += n
        }
        val perSecond = decoder.channels.toLong() * decoder.sampleRate
        return if (perSecond == 0L) 0f else (samples.toDouble() / perSecond).toFloat()
    }
}
