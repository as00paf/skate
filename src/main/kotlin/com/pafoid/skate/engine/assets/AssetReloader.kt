package com.pafoid.skate.engine.assets

import java.io.File

/**
 * Polling file watcher for hot reload. A change (modified time or size) triggers the entry's reload callback
 * once the file has been stable for two consecutive polls, so half-written files are not loaded.
 * Call [poll] from the main thread; callbacks run on the calling thread.
 */
class AssetReloader(private val onError: (String, Throwable) -> Unit = { _, _ -> }) {

    private class Entry(val file: File, val reload: (File) -> Unit) {
        var stamp: Stamp = Stamp.of(file)
        var pending: Stamp? = null
    }

    private data class Stamp(val modified: Long, val length: Long) {
        companion object {
            fun of(file: File) = Stamp(file.lastModified(), file.length())
        }
    }

    private val entries = LinkedHashMap<String, Entry>()

    val watchedKeys: Set<String> get() = entries.keys

    fun watch(key: String, file: File, reload: (File) -> Unit) {
        if (key !in entries) entries[key] = Entry(file, reload)
    }

    fun unwatch(key: String) {
        entries.remove(key)
    }

    /** Returns the keys that were reloaded by this poll. */
    fun poll(): List<String> {
        val reloaded = ArrayList<String>()
        for ((key, entry) in entries.toList()) {
            if (!entry.file.exists()) continue
            val now = Stamp.of(entry.file)
            val pending = entry.pending
            when {
                pending != null && pending == now -> {
                    entry.stamp = now
                    entry.pending = null
                    try {
                        entry.reload(entry.file)
                        reloaded += key
                    } catch (e: Exception) {
                        onError(key, e)
                    }
                }
                now != entry.stamp -> entry.pending = now
                else -> entry.pending = null
            }
        }
        return reloaded
    }
}
