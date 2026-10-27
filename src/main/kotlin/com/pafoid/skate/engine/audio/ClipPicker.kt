package com.pafoid.skate.engine.audio

import kotlin.random.Random

/** Chooses the clip for a (possibly random-container) sound, avoiding an immediate repeat. */
class ClipPicker(private val random: Random = Random.Default) {
    private val last = HashMap<Int, String>()

    fun pick(owner: Int, single: String, clips: List<String>): String {
        val candidates = clips.filter { it.isNotBlank() }
        if (candidates.isEmpty()) return single
        if (candidates.size == 1) return candidates[0]
        val previous = last[owner]
        val pool = candidates.filter { it != previous }.ifEmpty { candidates }
        return pool[random.nextInt(pool.size)].also { last[owner] = it }
    }

    fun forget(owner: Int) {
        last.remove(owner)
    }
}
