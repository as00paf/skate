package com.pafoid.skate.editor.search.data

/**
 * Helper data class to hold result with its category for flattened list.
 */
data class SearchResultWithCategory(
    val category: SearchCategory,
    val result: SearchResult
)