package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.SearchCategory
import com.pafoid.skate.editor.search.SearchResult
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.LightingComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import org.koin.core.component.KoinComponent

/**
 * Search provider for GameObjects in the current scene.
 *
 * This provider searches GameObject names using case-insensitive substring matching
 * and fuzzy matching for abbreviated queries. Results include hierarchy information
 * and support navigation to select the GameObject in the scene hierarchy.
 *
 * Search features:
 * - Case-insensitive substring matching
 * - Fuzzy matching for abbreviated queries (e.g., "cam" matches "Camera")
 * - Parent hierarchy path in description
 * - UID in metadata for precise identification
 *
 * Navigation:
 * - Selects the GameObject in SceneHierarchy via GameObjectManager
 * - The selected GameObject will be focused in viewport by existing GizmoSystem
 */
class GameObjectSearchProvider(
    private val sceneManager: SceneManager,
    private val stringManager: StringManager,
) : BaseSearchProvider(), KoinComponent {

    override val category: SearchCategory = SearchCategory.GAMEOBJECT

    override suspend fun search(query: String): List<SearchResult> {
        val scene = sceneManager.currentScene ?: return emptyList()
        val gameObjects = scene.gameObjectManager.gameObjects

        return gameObjects
            .mapNotNull { go ->
                val score = calculateRelevance(go.name, query)
                if (score > 0.0f) {
                    createSearchResult(go, score)
                } else {
                    null
                }
            }
            .sortedByDescending { it.relevanceScore }
    }

    override fun navigate(result: SearchResult) {
        val uid = result.metadata["uid"] as? Int ?: return
        val scene = sceneManager.currentScene ?: return
        val gameObject = scene.gameObjectManager.getGameObject(uid)
        gameObject?.let {
            scene.gameObjectManager.setSelectedGameObject(it)
        }
    }

    /**
     * Creates a SearchResult from a GameObject with the given relevance score.
     *
     * @param go The GameObject to create a result for
     * @param score The relevance score calculated during search
     * @return A SearchResult with all necessary display and navigation data
     */
    private fun createSearchResult(go: GameObject, score: Float): SearchResult {
        val hierarchyPath = buildHierarchyPath(go)
        val subcategory = determineSubcategory(go)
        val icon = determineIcon(go)

        return SearchResult(
            id = "go_${go.getUid()}",
            displayName = go.name,
            category = SearchCategory.GAMEOBJECT,
            subcategory = subcategory,
            description = hierarchyPath,
            icon = icon,
            relevanceScore = score,
            metadata = mapOf(
                "uid" to go.getUid(),
                "name" to go.name
            )
        )
    }

    /**
     * Builds the full hierarchy path for a GameObject.
     *
     * Example: "Parent > Child > GameObject"
     *
     * @param go The GameObject to build the path for
     * @return The hierarchy path as a string
     */
    private fun buildHierarchyPath(go: GameObject): String {
        val path = mutableListOf<String>()
        var current: GameObject? = go

        while (current != null) {
            path.add(current.name)
            current = current.parent
        }

        return path.asReversed().joinToString(" > ")
    }

    /**
     * Determines the subcategory based on the GameObject's components.
     *
     * @param go The GameObject to analyze
     * @return A subcategory string (e.g., "Skateboard", "Camera", or "GameObject")
     */
    private fun determineSubcategory(go: GameObject): String {
        return when {
            go.hasComponent<SkateboardPhysics>() -> "Skateboard"
            go.hasComponent<LightingComponent>() -> "Light"
            go.hasComponent<AudioComponent>() -> "Audio Source"
            go.hasComponent<RenderComponent>() -> "Mesh"
            else -> stringManager.getString("search.category.gameobject.default")
        }
    }

    /**
     * Determines the appropriate icon for a GameObject based on its components.
     *
     * @param go The GameObject to analyze
     * @return An icon identifier string
     */
    private fun determineIcon(go: GameObject): String {
        return when {
            go.hasComponent<SkateboardPhysics>() -> Icons.CUBE
            go.hasComponent<LightingComponent>() -> Icons.SUN
            go.hasComponent<AudioComponent>() -> Icons.MUSIC
            go.hasComponent<RenderComponent>() -> Icons.CUBE
            else -> Icons.CUBE
        }
    }
}
