package com.pafoid.skate.editor.search

import com.pafoid.skate.engine.ecs.SceneManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Central search engine that aggregates results from multiple [SearchProvider]s.
 *
 * The SearchEngine manages provider registration, executes searches asynchronously
 * across all providers, and aggregates results with proper scoring and sorting.
 *
 * This class is designed to be used with dependency injection (Koin) and integrates
 * with the engine's [SceneManager] and [com.pafoid.skate.engine.assets.ResourceManager].
 *
 * Usage:
 * ```
 * val engine: SearchEngine = get()
 * engine.registerProvider(myProvider)
 * val results = engine.search("skateboard")
 * ```
 */
class SearchEngine : KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val resourceManager: com.pafoid.skate.engine.assets.ResourceManager by inject()

    private val providers = mutableListOf<SearchProvider>()

    /**
     * Registers a search provider with this engine.
     *
     * Providers are queried during search operations. Registration should be
     * done during engine initialization.
     *
     * @param provider The provider to register
     */
    fun registerProvider(provider: SearchProvider) {
        providers.add(provider)
    }

    /**
     * Unregisters a search provider from this engine.
     *
     * @param provider The provider to unregister
     * @return True if the provider was registered and removed
     */
    fun unregisterProvider(provider: SearchProvider): Boolean {
        return providers.remove(provider)
    }

    /**
     * Executes a search across all registered providers asynchronously.
     *
     * Results are aggregated by category and sorted by relevance score.
     * This method uses coroutines to run all provider searches in parallel
     * for optimal performance.
     *
     * @param query The search query string
     * @return A map of categories to their respective search results
     */
    suspend fun search(query: String): Map<SearchCategory, List<SearchResult>> = coroutineScope {
        if (query.isBlank()) {
            return@coroutineScope emptyMap()
        }

        // Launch async search for each provider
        val deferredResults = providers.map { provider ->
            async {
                try {
                    provider.search(query)
                } catch (e: Exception) {
                    // Log error but continue with other providers
                    e.printStackTrace()
                    emptyList<SearchResult>()
                }
            }
        }

        // Aggregate results by category
        val resultsByCategory = mutableMapOf<SearchCategory, MutableList<SearchResult>>()

        deferredResults.forEach { deferred ->
            val results = deferred.await()
            results.forEach { result ->
                resultsByCategory
                    .getOrPut(result.category) { mutableListOf() }
                    .add(result)
            }
        }

        // Sort each category by relevance score (descending)
        resultsByCategory.forEach { (_, results) ->
            results.sortByDescending { it.relevanceScore }
        }

        resultsByCategory
    }

    /**
     * Navigates to a search result using its provider.
     *
     * This method finds the provider that owns the result's category and
     * delegates navigation to it.
     *
     * @param result The search result to navigate to
     * @return True if navigation was successful
     */
    fun navigate(result: SearchResult): Boolean {
        val provider = providers.find { it.category == result.category }
        return provider?.let {
            it.navigate(result)
            true
        } ?: false
    }

    /**
     * Gets all registered providers.
     *
     * @return List of registered search providers
     */
    fun getProviders(): List<SearchProvider> = providers.toList()

    /**
     * Checks if a provider for the given category is registered.
     *
     * @param category The category to check
     * @return True if a provider for this category exists
     */
    fun hasProvider(category: SearchCategory): Boolean {
        return providers.any { it.category == category }
    }
}
