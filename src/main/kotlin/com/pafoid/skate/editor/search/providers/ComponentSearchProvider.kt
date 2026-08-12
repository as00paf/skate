package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager

class ComponentSearchProvider(
    private val sceneManager: SceneManager,
    private val gameObjectManager: GameObjectManager,
    private val stringManager: StringManager,
) : BaseSearchProvider() {
    override val category: SearchCategory = SearchCategory.COMPONENT

    override suspend fun search(query: String): List<SearchResult> {
        val scene = sceneManager.currentScene ?: return emptyList()
        val gameObjects = scene.gameObjects

        return gameObjects
            .flatMap { go ->
                go.components
                    .mapNotNull { component ->
                        val score = calculateRelevance(component.javaClass.simpleName, query)
                        if (score > 0.0f) {
                            createSearchResult(go, component, score)
                        } else {
                            null
                        }
                    }
            }
            .sortedByDescending { it.relevanceScore }
    }

    override fun navigate(result: SearchResult) {
        val gameObjectUid = result.metadata["gameObjectUid"] as? Int ?: return
        val scene = sceneManager.currentScene ?: return
        val gameObject = gameObjectManager.getGameObject(gameObjectUid)
        gameObject?.let {
            scene.selectedGameObject = it
        }
    }

    private fun createSearchResult(go: GameObject, component: Component, score: Float): SearchResult {
        val componentType = component.javaClass.simpleName
        val icon = determineIcon(component)

        return SearchResult(
            id = "comp_${go.uId}_${componentType}",
            displayName = componentType,
            category = SearchCategory.COMPONENT,
            subcategory = "Component",
            description = "${stringManager.getString("search.everywhere.category.component")} on ${go.name}",
            icon = icon,
            relevanceScore = score,
            metadata = mapOf(
                "gameObjectUid" to go.uId,
                "componentType" to componentType
            )
        )
    }

    private fun determineIcon(component: Component): String {
        return when (component) {
            is Transform -> Icons.CUBE
            else -> Icons.MICROCHIP
        }
    }
}
