package com.pafoid.skate.editor.search

import com.pafoid.skate.editor.search.history.SearchHistory
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-18: search history persists to the given (user-data) file and reports problems through the logger. */
class SearchHistoryTest {

    @TempDir
    lateinit var dir: File

    private val logger = mockk<LoggerService>(relaxed = true)

    @Test
    fun SavesAndReloads_InTheGivenFile_CreatingItsFolder() = runBlocking {
        val file = File(dir, "nested/search_history.json")
        SearchHistory(Serializer(), logger, file).add("skateboard")
        assertTrue(file.isFile)
        assertEquals(listOf("skateboard"), SearchHistory(Serializer(), logger, file).getRecent().map { it.query })
    }

    @Test
    fun CorruptFile_StartsEmptyAndLogsAWarning() = runBlocking {
        val file = File(dir, "search_history.json").apply { writeText("{not json") }
        val history = SearchHistory(Serializer(), logger, file)
        assertTrue(history.getRecent().isEmpty())
        verify { logger.logEditor(match { it.contains("search history") }, LogLevel.WARN) }
    }
}
