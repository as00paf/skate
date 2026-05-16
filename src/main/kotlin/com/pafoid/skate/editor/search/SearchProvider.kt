package com.pafoid.skate.editor.search

import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult

/**
 * Provider interface for searchable data sources in the editor.
 *
 * Each provider is responsible for searching a specific category of resources
 * and navigating to selected results. Providers are registered with the [SearchEngine]
 * and queried asynchronously during search operations.
 *
 * Implementations should be thread-safe and support concurrent search calls.
 * Search operations should be efficient and return results quickly.
 */
interface SearchProvider {
    /**
     * The category of resources this provider searches.
     */
    val category: SearchCategory

    /**
     * Performs an asynchronous search for the given query.
     *
     * This method should return a list of search results matching the query.
     * Results should include relevance scores for proper sorting.
     *
     * @param query The search query string
     * @return List of [com.pafoid.skate.editor.search.data.SearchResult] objects matching the query
     */
    suspend fun search(query: String): List<SearchResult>

    /**
     * Navigates to the resource represented by the given search result.
     *
     * This method is called when a user selects a search result.
     * Implementations should perform the appropriate navigation action
     * (e.g., select GameObject, open asset, execute action).
     *
     * @param result The search result to navigate to
     */
    fun navigate(result: SearchResult)
}

/**
 * Base abstract class providing common utilities for search providers.
 *
 * This class implements common fuzzy matching logic that can be reused
 * across different provider implementations.
 */
abstract class BaseSearchProvider : SearchProvider {

    /**
     * Calculates a relevance score for a simple contains match.
     *
     * @param target The string to search in
     * @param query The search query
     * @return A score from 0.0f to 1.0f, or 0.0f if no match
     */
    protected fun calculateRelevance(target: String, query: String): Float {
        if (query.isBlank() || target.isBlank()) return 0.0f

        val targetLower = target.lowercase()
        val queryLower = query.lowercase()

        // Exact match gets highest score
        if (targetLower == queryLower) return 1.0f

        // Starts with match gets high score
        if (targetLower.startsWith(queryLower)) return 0.9f

        // Contains match with score based on position
        val index = targetLower.indexOf(queryLower)
        return if (index >= 0) {
            // Earlier matches score higher
            0.7f - (index.toFloat() / target.length) * 0.3f
        } else {
            0.0f
        }
    }

    /**
     * Performs fuzzy matching on the target string.
     *
     * Fuzzy matching allows for abbreviated queries like "sk8brd" to match
     * "Skateboard". Returns a score indicating match quality.
     *
     * @param target The string to search in
     * @param query The fuzzy search query
     * @return A score from 0.0f to 1.0f, or 0.0f if no match
     */
    protected fun fuzzyMatch(target: String, query: String): Float {
        if (query.isBlank() || target.isBlank()) return 0.0f

        val targetLower = target.lowercase()
        val queryLower = query.lowercase()

        // Try exact match first
        val exactScore = calculateRelevance(target, query)
        if (exactScore > 0.0f) return exactScore

        // Fuzzy match: all query characters must appear in order
        var targetIndex = 0
        var matchCount = 0
        var firstMatchIndex = -1

        for (queryChar in queryLower) {
            while (targetIndex < targetLower.length) {
                if (targetLower[targetIndex] == queryChar) {
                    if (firstMatchIndex < 0) firstMatchIndex = targetIndex
                    matchCount++
                    targetIndex++
                    break
                }
                targetIndex++
            }
        }

        return if (matchCount == queryLower.length) {
            // Score based on how compact the match is
            val matchSpan = targetIndex - firstMatchIndex
            val compactness = queryLower.length.toFloat() / matchSpan
            0.5f + (compactness * 0.4f)
        } else {
            0.0f
        }
    }

    /**
     * Checks if the target contains the query as a substring.
     *
     * @param target The string to search in
     * @param query The search query
     * @return True if target contains query (case-insensitive)
     */
    protected fun containsMatch(target: String, query: String): Boolean {
        if (query.isBlank() || target.isBlank()) return false
        return target.lowercase().contains(query.lowercase())
    }
}
