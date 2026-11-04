package com.pafoid.skate.engine.audio

import fr.delthas.javamp3.Sound
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Streams an MP3 file as 16-bit PCM with JavaMP3 (fr.delthas:javamp3, MIT, pure Java). Only MPEG-1 Layer III
 * (32/44.1/48 kHz, what music is normally encoded as) is supported; MPEG-2/2.5 files fail with an [IOException].
 * ID3v2 tags (cover art included) are skipped. Decoder failures are reported as [IOException].
 */
class Mp3Decoder(private val file: File) : PcmStreamDecoder {
    private var sound: Sound = open()
    override val channels: Int = if (sound.isStereo) 2 else 1
    override val sampleRate: Int = sound.samplingFrequency
    private var bytes = ByteArray(0)

    constructor(path: String) : this(File(path))

    private fun open(): Sound {
        val input = BufferedInputStream(FileInputStream(file), 64 * 1024)
        return try {
            Sound(input)
        } catch (e: Exception) {
            input.close()
            throw IOException("Cannot decode MP3 '${file.path}' (only MPEG-1 Layer III is supported): ${e.message ?: e}", e)
        }
    }

    override fun read(out: ShortArray): Int {
        val frameBytes = channels * 2
        val wanted = (out.size * 2) - (out.size * 2) % frameBytes
        if (bytes.size < wanted) bytes = ByteArray(wanted)
        var filled = 0
        try {
            while (filled < wanted) {
                val n = sound.read(bytes, filled, wanted - filled)
                if (n <= 0) break
                filled += n
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Corrupt MP3 data in '${file.path}': ${e.message ?: e}", e)
        }
        val samples = (filled - filled % frameBytes) / 2
        for (i in 0 until samples) {
            out[i] = ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
        }
        return samples
    }

    override fun rewind() {
        sound.close()
        sound = open()
    }

    override fun close() = sound.close()

    /** Decodes the whole file (for short, non-streamed sounds). */
    fun readAll(): ShortArray {
        val result = ArrayList<ShortArray>()
        val chunk = ShortArray(16384)
        var total = 0
        while (true) {
            val n = read(chunk)
            if (n == 0) break
            result += chunk.copyOf(n)
            total += n
        }
        val pcm = ShortArray(total)
        var at = 0
        result.forEach { it.copyInto(pcm, at); at += it.size }
        return pcm
    }
}

/** Opens the streaming decoder for a sound file by extension (OGG through stb_vorbis, MP3 through [Mp3Decoder]). */
object PcmDecoders {
    val STREAMABLE_EXTENSIONS = setOf("ogg", "mp3")

    fun open(path: String): PcmStreamDecoder = when (File(path).extension.lowercase()) {
        "ogg" -> StbVorbisDecoder(path)
        "mp3" -> Mp3Decoder(path)
        else -> throw IOException("Cannot stream '$path': only ${STREAMABLE_EXTENSIONS.joinToString()} are streamable")
    }
}
