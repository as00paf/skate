package com.pafoid.skate.engine.audio

import kotlinx.serialization.Serializable

@Serializable
enum class StreamMode { AUTO, ALWAYS, NEVER }

object StreamPolicy {
    const val AUTO_THRESHOLD_BYTES = 1_000_000L

    /** Only OGG can be streamed. AUTO streams music-bus sounds and any large file. */
    fun shouldStream(path: String, bus: BusId, mode: StreamMode, fileSizeBytes: Long): Boolean {
        if (!path.endsWith(".ogg", ignoreCase = true)) return false
        return when (mode) {
            StreamMode.NEVER -> false
            StreamMode.ALWAYS -> true
            StreamMode.AUTO -> bus == BusId.MUSIC || fileSizeBytes > AUTO_THRESHOLD_BYTES
        }
    }
}
