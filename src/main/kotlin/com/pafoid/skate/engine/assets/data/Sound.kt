package com.pafoid.skate.engine.assets.data

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.openal.AL10.AL_BUFFER
import org.lwjgl.openal.AL10.AL_FORMAT_MONO16
import org.lwjgl.openal.AL10.AL_FORMAT_MONO8
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO16
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO8
import org.lwjgl.openal.AL10.AL_GAIN
import org.lwjgl.openal.AL10.AL_LOOPING
import org.lwjgl.openal.AL10.AL_POSITION
import org.lwjgl.openal.AL10.AL_SOURCE_STATE
import org.lwjgl.openal.AL10.AL_STOPPED
import org.lwjgl.openal.AL10.alBufferData
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.openal.AL10.alDeleteSources
import org.lwjgl.openal.AL10.alGenBuffers
import org.lwjgl.openal.AL10.alGenSources
import org.lwjgl.openal.AL10.alGetSourcei
import org.lwjgl.openal.AL10.alSourcePlay
import org.lwjgl.openal.AL10.alSourceStop
import org.lwjgl.openal.AL10.alSourcef
import org.lwjgl.openal.AL10.alSourcei
import org.lwjgl.stb.STBVorbis.stb_vorbis_decode_filename
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.libc.LibCStdlib.free
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/**
 * Sound resource for OpenAL audio playback.
 *
 * Loads and manages a single audio buffer and source.
 * Supports WAV (via Java Sound) and OGG (via STB Vorbis) formats.
 */
class Sound(
    val filePath: String,
    val loops: Boolean = false
) : KoinComponent {

    private val logger: LoggerService by inject()
    private var bufferId: Int = -1
    private var sourceId: Int = -1
    private var isPlaying = false

    init {
        load()
    }

    private fun load() {
        val file = File(filePath)
        if (!file.exists()) {
            logger.logEngine("Sound: File not found '$filePath'", LogLevel.ERROR)
            return
        }

        try {
            when (file.extension.lowercase()) {
                "ogg" -> loadOgg(file.path)
                "wav" -> loadWav(file.path)
                else -> {
                    logger.logEngine("Sound: Unsupported format '${file.extension}' for '$filePath'", LogLevel.ERROR)
                }
            }
        } catch (e: Exception) {
            logger.logEngine("Sound: Exception loading '$filePath' - ${e.message}", LogLevel.ERROR)
            e.printStackTrace()
        }
    }

    private fun loadOgg(path: String) {
        MemoryStack.stackPush().use { stack ->
            val channelsBuffer = stack.mallocInt(1)
            val sampleRateBuffer = stack.mallocInt(1)

            logger.logEngine("Sound: Loading OGG '$path'", LogLevel.INFO)
            val rawAudioBuffer = stb_vorbis_decode_filename(path, channelsBuffer, sampleRateBuffer)
                ?: run {
                    logger.logEngine("Sound: STB Vorbis failed to load OGG '$path'", LogLevel.ERROR)
                    return
                }

            val channels = channelsBuffer.get()
            val sampleRate = sampleRateBuffer.get()
            logger.logEngine("Sound: Loaded OGG '$path' - ${channels} channels, ${sampleRate}Hz", LogLevel.INFO)
            val format = if (channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16

            bufferId = alGenBuffers()
            alBufferData(bufferId, format, rawAudioBuffer, sampleRate)
            free(rawAudioBuffer)

            createSource()
        }
    }

    private fun loadWav(path: String) {
        logger.logEngine("Sound: Loading WAV '$path'", LogLevel.INFO)

        val audioInputStream = AudioSystem.getAudioInputStream(File(path))
        var format = audioInputStream.format
        var bytes = audioInputStream.readAllBytes()
        audioInputStream.close()

        // Convert 24-bit or 32-bit to 16-bit for OpenAL compatibility
        if (format.sampleSizeInBits > 16) {
            logger.logEngine("Sound: Converting ${format.sampleSizeInBits}-bit to 16-bit", LogLevel.INFO)
            bytes = convertTo16Bit(bytes, format)
            format = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.sampleRate,
                16,
                format.channels,
                format.channels * 2,
                format.sampleRate,
                format.isBigEndian
            )
        }

        val alFormat = when {
            format.channels == 1 && format.sampleSizeInBits == 8 -> AL_FORMAT_MONO8
            format.channels == 1 && format.sampleSizeInBits == 16 -> AL_FORMAT_MONO16
            format.channels == 2 && format.sampleSizeInBits == 8 -> AL_FORMAT_STEREO8
            format.channels == 2 && format.sampleSizeInBits == 16 -> AL_FORMAT_STEREO16
            else -> {
                logger.logEngine(
                    "Sound: Unsupported WAV format after conversion: ${format.channels} channels, ${format.sampleSizeInBits} bits",
                    LogLevel.ERROR
                )
                return
            }
        }

        logger.logEngine(
            "Sound: Loaded WAV '$path' - ${format.channels} channels, ${format.sampleRate}Hz, ${format.sampleSizeInBits} bits",
            LogLevel.INFO
        )

        // Convert ByteArray to ByteBuffer for OpenAL
        val buffer = MemoryUtil.memAlloc(bytes.size)
        buffer.put(bytes)
        buffer.flip()

        bufferId = alGenBuffers()
        alBufferData(bufferId, alFormat, buffer, format.sampleRate.toInt())
        MemoryUtil.memFree(buffer)

        createSource()
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

    private fun createSource() {
        sourceId = alGenSources()
        alSourcei(sourceId, AL_BUFFER, bufferId)
        alSourcei(sourceId, AL_LOOPING, if (loops) 1 else 0)
        alSourcef(sourceId, AL_GAIN, 0.3f)
        logger.logEngine("Sound: Successfully loaded", LogLevel.INFO)
    }

    fun delete() {
        if (sourceId != -1) alDeleteSources(sourceId)
        if (bufferId != -1) alDeleteBuffers(bufferId)
    }

    fun play() {
        if (alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_STOPPED) {
            isPlaying = false
            alSourcei(sourceId, AL_POSITION, 0)
        }

        if (!isPlaying) {
            alSourcePlay(sourceId)
            isPlaying = true
        }
    }

    fun stop() {
        if (isPlaying) {
            alSourceStop(sourceId)
            isPlaying = false
        }
    }

    fun isPlaying(): Boolean {
        if (alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_STOPPED) {
            isPlaying = false
        }
        return isPlaying
    }
}
