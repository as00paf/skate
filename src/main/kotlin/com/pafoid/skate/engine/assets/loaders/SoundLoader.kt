package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.audio.Mp3Decoder
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import org.lwjgl.openal.AL10.AL_FORMAT_MONO16
import org.lwjgl.openal.AL10.AL_FORMAT_MONO8
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO16
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO8
import org.lwjgl.openal.AL10.alBufferData
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.openal.AL10.alGenBuffers
import org.lwjgl.stb.STBVorbis.stb_vorbis_decode_filename
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.io.extension
import kotlin.use

class SoundLoader(private val logger: LoggerService) {
    fun load(filePath: String): SoundBuffer {
        val file = File(filePath)
        if (!file.exists()) {
            logger.log("SoundBuffer: File not found '$filePath'", LogLevel.ERROR)
            throw FileNotFoundException(filePath)
        }

        try {
            return when (file.extension.lowercase()) {
                "ogg" -> loadOgg(file.path)
                "wav" -> loadWav(file.path)
                "mp3" -> loadMp3(file.path)
                else -> {
                    logger.log(
                        "SoundBuffer: Unsupported format '${file.extension}' for '$filePath'",
                        LogLevel.ERROR
                    )
                    throw IOException("Unsupported format '${file.extension}' for '$filePath'")
                }
            }
        } catch (e: Exception) {
            logger.logError("SoundBuffer: Exception loading '$filePath'", e)
            throw e
        }
    }

    fun delete(sound: SoundBuffer) {
        if (sound.bufferId != -1) alDeleteBuffers(sound.bufferId)
        sound.bufferId = -1
    }

    private fun loadOgg(path: String): SoundBuffer {
        MemoryStack.stackPush().use { stack ->
            val channelsBuffer = stack.mallocInt(1)
            val sampleRateBuffer = stack.mallocInt(1)

            logger.log("SoundBuffer: Loading OGG '$path'", LogLevel.INFO)
            val rawAudioBuffer = stb_vorbis_decode_filename(path, channelsBuffer, sampleRateBuffer)
                ?: run {
                    logger.log("SoundBuffer: STB Vorbis failed to load OGG '$path'", LogLevel.ERROR)
                    throw IOException("STB Vorbis failed to load OGG '$path'")
                }

            val channels = channelsBuffer.get()
            val sampleRate = sampleRateBuffer.get()
            logger.log("SoundBuffer: Loaded OGG '$path' - ${channels} channels, ${sampleRate}Hz", LogLevel.INFO)
            val format = if (channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
            val soundBuffer = SoundBuffer(path)
            soundBuffer.durationInSeconds = rawAudioBuffer.limit().toFloat() / channels / sampleRate

            soundBuffer.bufferId = alGenBuffers()
            alBufferData(soundBuffer.bufferId, format, rawAudioBuffer, sampleRate)
            // Fix resource leaks per requirements
            MemoryUtil.memFree(rawAudioBuffer)
            return soundBuffer
        }
    }

    /** 8/16-bit PCM ready for `alBufferData`. */
    class DecodedWav(val pcm: ByteArray, val channels: Int, val bitsPerSample: Int, val sampleRate: Int) {
        val alFormat: Int
            get() = when {
                channels == 1 && bitsPerSample == 8 -> AL_FORMAT_MONO8
                channels == 1 && bitsPerSample == 16 -> AL_FORMAT_MONO16
                channels == 2 && bitsPerSample == 8 -> AL_FORMAT_STEREO8
                else -> AL_FORMAT_STEREO16
            }
        val durationInSeconds: Float get() = pcm.size.toFloat() / (channels * bitsPerSample / 8) / sampleRate
    }

    /** Decodes a WAV file to 8/16-bit PCM without touching OpenAL (24/32-bit input is converted to 16-bit). */
    fun decodeWav(file: File): DecodedWav {
        var format: AudioFormat
        var bytes: ByteArray
        AudioSystem.getAudioInputStream(file).use { audioInputStream ->
            format = audioInputStream.format
            bytes = audioInputStream.readAllBytes()
        }
        if (format.sampleSizeInBits > 16) {
            logger.log("SoundBuffer: Converting ${format.sampleSizeInBits}-bit to 16-bit", LogLevel.INFO)
            bytes = convertTo16Bit(bytes, format)
            format = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, format.sampleRate, 16, format.channels,
                format.channels * 2, format.sampleRate, format.isBigEndian
            )
        }
        if (format.channels !in 1..2 || format.sampleSizeInBits !in setOf(8, 16)) {
            throw IOException(
                "SoundBuffer: Unsupported WAV format after conversion: ${format.channels} channels, ${format.sampleSizeInBits} bits"
            )
        }
        return DecodedWav(bytes, format.channels, format.sampleSizeInBits, format.sampleRate.toInt())
    }

    /** Short MP3s are decoded whole; long ones are streamed by AudioSystem instead (see StreamPolicy). */
    private fun loadMp3(path: String): SoundBuffer {
        logger.log("SoundBuffer: Loading MP3 '$path'", LogLevel.INFO)
        Mp3Decoder(path).use { decoder ->
            val pcm = decoder.readAll()
            val soundBuffer = SoundBuffer(path)
            soundBuffer.durationInSeconds = pcm.size.toFloat() / decoder.channels / decoder.sampleRate
            soundBuffer.bufferId = alGenBuffers()
            alBufferData(soundBuffer.bufferId, if (decoder.channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16, pcm, decoder.sampleRate)
            return soundBuffer
        }
    }

    private fun loadWav(path: String): SoundBuffer {
        logger.log("SoundBuffer: Loading WAV '$path'", LogLevel.INFO)
        val wav = decodeWav(File(path))
        logger.log(
            "SoundBuffer: Loaded WAV '$path' - ${wav.channels} channels, ${wav.sampleRate}Hz, ${wav.bitsPerSample} bits",
            LogLevel.INFO
        )
        val soundBuffer = SoundBuffer(path)
        soundBuffer.durationInSeconds = wav.durationInSeconds

        val buffer = MemoryUtil.memAlloc(wav.pcm.size)
        buffer.put(wav.pcm)
        buffer.flip()
        soundBuffer.bufferId = alGenBuffers()
        alBufferData(soundBuffer.bufferId, wav.alFormat, buffer, wav.sampleRate)
        MemoryUtil.memFree(buffer)
        return soundBuffer
    }

    /**
     * Converts 24-bit or 32-bit audio data to 16-bit.
     */
    private fun convertTo16Bit(bytes: ByteArray, format: AudioFormat): ByteArray {
        val bytesPerSample = format.sampleSizeInBits / 8
        val result = ByteArrayOutputStream(bytes.size / bytesPerSample * 2)

        for (i in bytes.indices step bytesPerSample * format.channels) {
            for (ch in 0 until format.channels) {
                val offset = i + ch * bytesPerSample
                if (offset + bytesPerSample <= bytes.size) {
                    // Read 24/32-bit sample (little-endian)
                    var sample = 0
                    for (b in 0 until bytesPerSample) {
                        sample = sample or ((bytes[offset + b].toInt() and 0xFF) shl (b * 8))
                    }
                    // Convert to signed
                    if (sample and (1 shl (bytesPerSample * 8 - 1)) != 0) {
                        sample = sample - (1 shl (bytesPerSample * 8))
                    }
                    // Scale to 16-bit
                    val sample16 = (sample shr (bytesPerSample * 8 - 16)).toShort()
                    // Write little-endian
                    result.write(sample16.toInt() and 0xFF)
                    result.write((sample16.toInt() shr 8) and 0xFF)
                }
            }
        }

        return result.toByteArray()
    }
}