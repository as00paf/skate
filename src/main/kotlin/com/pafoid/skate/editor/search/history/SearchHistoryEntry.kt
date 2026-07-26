package com.pafoid.skate.editor.search.history

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long,
    val resultId: String? = null,
    val category: String? = null
)