package com.pafoid.skate.engine.audio

/**
 * Tracks which owners currently hold one of a limited number of voices and decides which voice to steal.
 * Higher [priority] is more important. Pure bookkeeping so it can be tested without OpenAL.
 */
class VoiceManager<K : Any>(val maxVoices: Int = DEFAULT_MAX_VOICES) {

    private class Voice(val priority: Int, var audibility: Float, val startedAt: Long)

    sealed class Result {
        /** A voice was granted; [stolen] is the owner that lost its voice, if any. */
        data class Granted<K>(val stolen: K?) : Result()
        object Denied : Result()
    }

    private val voices = LinkedHashMap<K, Voice>()
    private var clock = 0L

    val activeCount: Int get() = voices.size

    fun isActive(key: K): Boolean = key in voices

    fun release(key: K) {
        voices.remove(key)
    }

    fun clear() = voices.clear()

    fun updateAudibility(key: K, audibility: Float) {
        voices[key]?.audibility = audibility
    }

    /** Requests a voice for [key]. An owner that already holds one keeps it, with updated ranking. */
    fun request(key: K, priority: Int, audibility: Float): Result {
        voices.remove(key)
        var stolen: K? = null
        if (voices.size >= maxVoices) {
            val victim = voices.entries.minWithOrNull(
                compareBy<Map.Entry<K, Voice>>({ it.value.priority }, { it.value.audibility }, { it.value.startedAt })
            ) ?: return Result.Denied
            if (!outranks(priority, audibility, victim.value)) return Result.Denied
            stolen = victim.key
            voices.remove(stolen)
        }
        voices[key] = Voice(priority, audibility, clock++)
        return Result.Granted(stolen)
    }

    // Ties are won by the newcomer so a steady stream of equal sounds replaces the oldest.
    private fun outranks(priority: Int, audibility: Float, victim: Voice): Boolean =
        priority > victim.priority || (priority == victim.priority && audibility >= victim.audibility)

    companion object {
        const val DEFAULT_MAX_VOICES = 32
    }
}
