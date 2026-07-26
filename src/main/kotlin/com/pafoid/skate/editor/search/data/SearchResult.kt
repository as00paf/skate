package com.pafoid.skate.editor.search.data

data class SearchResult(
    val id: String,
    val displayName: String,
    val category: SearchCategory,
    val subcategory: String,
    val description: String? = null,
    val icon: String? = null,
    val relevanceScore: Float = 0.0f,
    val metadata: Map<String, Any> = emptyMap()
)