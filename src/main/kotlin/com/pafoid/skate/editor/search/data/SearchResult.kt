package com.pafoid.skate.editor.search.data

/**
 * Represents a single search result from any [com.pafoid.skate.editor.search.SearchProvider].
 *
 * This data class encapsulates all information needed to display a search result
 * in the UI and navigate to the corresponding resource. Results are scored and
 * sorted by relevance during search aggregation.
 *
 * @property id Unique identifier for this result (e.g., "go_123", "asset_texture_456")
 * @property displayName Human-readable name shown in the search UI
 * @property category The category this result belongs to
 * @property subcategory More specific type within the category (e.g., "Texture", "Prefab")
 * @property description Optional description or path information for context
 * @property icon Optional icon identifier for visual representation in UI
 * @property relevanceScore Score from 0.0 to 1.0 indicating match quality
 * @property metadata Additional data needed for navigation (e.g., UID, path, type)
 */
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