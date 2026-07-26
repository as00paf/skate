package com.pafoid.skate.editor.search

import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult

interface SearchProvider {
    /**
     * The category of resources this provider searches.
     */
    val category: SearchCategory

    suspend fun search(query: String): List<SearchResult>

    fun navigate(result: SearchResult)
}

abstract class BaseSearchProvider : SearchProvider {

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
}
