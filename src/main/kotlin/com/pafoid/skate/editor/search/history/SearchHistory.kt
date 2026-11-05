package com.pafoid.skate.editor.search.history

import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.editor.systems.EditorPaths
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Recent Search Everywhere queries, kept in the user-data folder ([EditorPaths]). */
class SearchHistory(
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val historyFile: File = EditorPaths.file(EditorPaths.SEARCH_HISTORY_FILE),
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
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
            historyFile.parentFile?.mkdirs()
            historyFile.writeText(jsonString)
        } catch (e: Exception) {
            logger.logEditor("Could not save search history to ${historyFile.path}: ${e.message}", LogLevel.WARN)
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
            logger.logEditor("Could not read search history ${historyFile.path}, starting empty: ${e.message}", LogLevel.WARN)
            entries.clear()
        }
    }
}

