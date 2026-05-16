package com.pafoid.skate.editor.search.history

import kotlinx.serialization.Serializable

/**
 * Represents a single entry in the search history.
 *
 * @property query The search query that was entered
 * @property timestamp Unix timestamp when the search was performed
 * @property resultId ID of the result that was selected (if any)
 * @property category Category of the selected result (if any)
 */
@Serializable
data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long,
    val resultId: String? = null,
    val category: String? = null
)