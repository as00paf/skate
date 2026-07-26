package com.pafoid.skate.editor.search.history

import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.engine.assets.serialization.Serializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class SearchHistory(
    private val historyFile: File = File("search_history.json"),
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val serializer: Serializer
) {
    companion object {
        private const val DEFAULT_MAX_ENTRIES = 20
    }

    private val mutex = Mutex()
    private val entries = mutableListOf<SearchHistoryEntry>()

    init {
        load()
    }

    suspend fun add(query: String, result: SearchResult? = null) {
        mutex.withLock {
            entries.removeAll { it.query == query }

            val entry = SearchHistoryEntry(
                query = query,
                timestamp = System.currentTimeMillis(),
                resultId = result?.id,
                category = result?.category?.name
            )
            entries.add(entry)

            while (entries.size > maxEntries) {
                entries.removeAt(0)
            }

            save()
        }
    }

    suspend fun getRecent(limit: Int = maxEntries): List<SearchHistoryEntry> = mutex.withLock {
        entries.asReversed().take(limit)
    }

    suspend fun clear() {
        mutex.withLock {
            entries.clear()
            if (historyFile.exists()) {
                historyFile.delete()
            }
        }
    }

    suspend fun remove(query: String): Boolean = mutex.withLock {
        val removed = entries.removeAll { it.query == query }
        if (removed) {
            save()
        }
        removed
    }

    private fun save() {
        try {
            val data = SearchHistoryData(entries = entries)
            val jsonString = serializer.encode(data)
            historyFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun load() {
        try {
            if (historyFile.exists()) {
                val jsonString = historyFile.readText()
                val data = serializer.decode<SearchHistoryData>(jsonString)
                entries.clear()
                entries.addAll(data.entries.take(maxEntries))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            entries.clear()
        }
    }
}

