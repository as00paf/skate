package com.pafoid.skate.editor.search.history

import kotlinx.serialization.Serializable

/**
 * Serializable container for search history data.
 *
 * @property entries List of history entries
 */
@Serializable
data class SearchHistoryData(
    val entries: List<SearchHistoryEntry>
)