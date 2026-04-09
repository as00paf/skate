package com.pafoid.skate.editor.search.history

import com.pafoid.skate.editor.search.SearchResult
import com.pafoid.skate.engine.assets.serialization.Serializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Manages search history with persistence to disk.
 *
 * SearchHistory maintains a list of recent search queries, limited to the
 * most recent entries. History is persisted to a JSON file and loaded
 * on initialization.
 *
 * All operations are thread-safe using a mutex for synchronization.
 *
 * @param historyFile Path to the JSON file for persistence
 * @param maxEntries Maximum number of entries to keep (default: 20)
 */
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

    /**
     * Adds a search query to the history.
     *
     * If the query already exists, it is moved to the most recent position.
     * If the history exceeds maxEntries, the oldest entry is removed.
     *
     * @param query The search query to add
     * @param result The result that was selected (optional)
     */
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

    /**
     * Gets the most recent search queries.
     *
     * Returns entries in reverse chronological order (most recent first).
     *
     * @param limit Maximum number of entries to return (default: all)
     * @return List of recent search queries
     */
    suspend fun getRecent(limit: Int = maxEntries): List<SearchHistoryEntry> = mutex.withLock {
        entries.asReversed().take(limit)
    }

    /**
     * Clears all search history.
     *
     * This removes all entries and deletes the history file.
     */
    suspend fun clear() {
        mutex.withLock {
            entries.clear()
            if (historyFile.exists()) {
                historyFile.delete()
            }
        }
    }

    /**
     * Removes a specific query from the history.
     *
     * @param query The query to remove
     * @return True if the query was found and removed
     */
    suspend fun remove(query: String): Boolean = mutex.withLock {
        val removed = entries.removeAll { it.query == query }
        if (removed) {
            save()
        }
        removed
    }

    /**
     * Saves the current history to the JSON file.
     */
    private fun save() {
        try {
            val data = SearchHistoryData(entries = entries)
            val jsonString = serializer.encode(data)
            historyFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads history from the JSON file.
     *
     * If the file doesn't exist or is invalid, starts with empty history.
     */
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

