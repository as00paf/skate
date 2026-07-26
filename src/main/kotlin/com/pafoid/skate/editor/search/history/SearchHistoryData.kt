package com.pafoid.skate.editor.search.history

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistoryData(
    val entries: List<SearchHistoryEntry>
)