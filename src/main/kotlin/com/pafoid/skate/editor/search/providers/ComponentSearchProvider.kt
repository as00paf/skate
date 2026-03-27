package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.SearchCategory
import com.pafoid.skate.editor.search.SearchResult
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Search provider for components on all GameObjects in the scene.
 *
 * This provider searches component type names using case-insensitive substring matching.
 * Results include the GameObject name and support navigation to select the GameObject
 * in the scene hierarchy with the component visible in PropertiesWindow.
 *
 * Search features:
 * - Case-insensitive component type name matching
 * - Searches all components on all GameObjects in the current scene
 * - GameObject name included in description for context
 * - Component type and GameObject UID in metadata for navigation
 *
 * Navigation:
 * - Selects the GameObject in SceneHierarchy via GameObjectManager
 * - The PropertiesWindow will display the GameObject's components
 * - User can then expand the specific component in the UI
 */
class ComponentSearchProvider : BaseSearchProvider(), KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val stringManager: StringManager by inject()

    override val category: SearchCategory = SearchCategory.COMPONENT

    override suspend fun search(query: String): List<SearchResult> {
        val scene = sceneManager.currentScene ?: return emptyList()
        val gameObjects = scene.gameObjectManager.gameObjects

        return gameObjects
            .flatMap { go ->
                go.getAllComponents()
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
        val gameObject = scene.gameObjectManager.getGameObject(gameObjectUid)
        gameObject?.let {
            scene.gameObjectManager.setSelectedGameObject(it)
        }
    }

    /**
     * Creates a SearchResult from a GameObject and Component with the given relevance score.
     *
     * @param go The GameObject containing the component
     * @param component The Component to create a result for
     * @param score The relevance score calculated during search
     * @return A SearchResult with all necessary display and navigation data
     */
    private fun createSearchResult(go: GameObject, component: Component, score: Float): SearchResult {
        val componentType = component.javaClass.simpleName
        val icon = determineIcon(component)

        return SearchResult(
            id = "comp_${go.getUid()}_${componentType}",
            displayName = componentType,
            category = SearchCategory.COMPONENT,
            subcategory = "Component",
            description = "${stringManager.getString("search.everywhere.category.component")} on ${go.name}",
            icon = icon,
            relevanceScore = score,
            metadata = mapOf(
                "gameObjectUid" to go.getUid(),
                "componentType" to componentType
            )
        )
    }

    /**
     * Determines the appropriate icon for a component based on its type.
     *
     * @param component The component to analyze
     * @return An icon identifier string
     */
    private fun determineIcon(component: Component): String {
        return when (component) {
            is Transform -> Icons.CUBE
            else -> Icons.MICROCHIP
        }
    }
}
