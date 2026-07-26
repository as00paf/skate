package com.pafoid.skate.editor.search

import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.editor.search.providers.ActionSearchProvider
import com.pafoid.skate.editor.search.providers.AssetSearchProvider
import com.pafoid.skate.editor.search.providers.ComponentSearchProvider
import com.pafoid.skate.editor.search.providers.GameObjectSearchProvider
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SearchEngine(
    engine: Engine,
    private val stringManager: StringManager,
) {
    private val providers = mutableListOf<SearchProvider>()

    init {
        registerProvider(GameObjectSearchProvider(engine.sceneManager, engine.gameObjectManager, stringManager))
        registerProvider(AssetSearchProvider(engine.logger))
        registerProvider(ComponentSearchProvider(engine.sceneManager, engine.gameObjectManager, stringManager))
        registerProvider(ActionSearchProvider(engine.sceneManager, engine.logger, engine.eventSystem))
    }

    fun registerProvider(provider: SearchProvider) {
        providers.add(provider)
    }

    fun unregisterProvider(provider: SearchProvider): Boolean {
        return providers.remove(provider)
    }

    suspend fun search(query: String): Map<SearchCategory, List<SearchResult>> = coroutineScope {
        if (query.isBlank()) {
            return@coroutineScope emptyMap()
        }

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

        val resultsByCategory = mutableMapOf<SearchCategory, MutableList<SearchResult>>()

        deferredResults.forEach { deferred ->
            val results = deferred.await()
            results.forEach { result ->
                resultsByCategory
                    .getOrPut(result.category) { mutableListOf() }
                    .add(result)
            }
        }

        resultsByCategory.forEach { (_, results) ->
            results.sortByDescending { it.relevanceScore }
        }

        resultsByCategory
    }

    fun navigate(result: SearchResult): Boolean {
        val provider = providers.find { it.category == result.category }
        return provider?.let {
            it.navigate(result)
            true
        } ?: false
    }

    fun getProviders(): List<SearchProvider> = providers.toList()

    fun hasProvider(category: SearchCategory): Boolean {
        return providers.any { it.category == category }
    }
}
