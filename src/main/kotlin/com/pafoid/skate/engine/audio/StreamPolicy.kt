package com.pafoid.skate.engine.audio

import kotlinx.serialization.Serializable

@Serializable
enum class StreamMode { AUTO, ALWAYS, NEVER }

object StreamPolicy {
    const val AUTO_THRESHOLD_BYTES = 1_000_000L

    /** OGG and MP3 can be streamed (WAV is always loaded whole). AUTO streams music-bus sounds and any large file. */
    fun shouldStream(path: String, bus: BusId, mode: StreamMode, fileSizeBytes: Long): Boolean {
        if (path.substringAfterLast('.', "").lowercase() !in PcmDecoders.STREAMABLE_EXTENSIONS) return false
        return when (mode) {
            StreamMode.NEVER -> false
            StreamMode.ALWAYS -> true
            StreamMode.AUTO -> bus == BusId.MUSIC || fileSizeBytes > AUTO_THRESHOLD_BYTES
        }
    }
}
