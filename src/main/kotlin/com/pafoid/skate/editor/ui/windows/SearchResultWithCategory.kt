package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.search.SearchCategory
import com.pafoid.skate.editor.search.SearchResult

/**
 * Helper data class to hold result with its category for flattened list.
 */
data class SearchResultWithCategory(
    val category: SearchCategory,
    val result: SearchResult
)