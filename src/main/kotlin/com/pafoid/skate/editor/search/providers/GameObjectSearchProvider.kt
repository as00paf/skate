package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.hasComponent

class GameObjectSearchProvider(
    private val sceneManager: SceneManager,
    private val gameObjectManager: GameObjectManager,
    private val stringManager: StringManager,
) : BaseSearchProvider() {

    override val category: SearchCategory = SearchCategory.GAMEOBJECT

    override suspend fun search(query: String): List<SearchResult> {
        val scene = sceneManager.currentScene ?: return emptyList()
        val gameObjects = scene.gameObjects

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
        val scene = sceneManager.currentScene ?: return
        val uid = result.metadata["uid"] as? Int ?: return
        val gameObject = gameObjectManager.getGameObject(uid)
        gameObject?.let {
            scene.selectedGameObject = it
        }
    }

    private fun createSearchResult(go: GameObject, score: Float): SearchResult {
        val hierarchyPath = buildHierarchyPath(go)
        val subcategory = determineSubcategory(go)
        val icon = determineIcon(go)

        return SearchResult(
            id = "go_${go.uId}",
            displayName = go.name,
            category = SearchCategory.GAMEOBJECT,
            subcategory = subcategory,
            description = hierarchyPath,
            icon = icon,
            relevanceScore = score,
            metadata = mapOf(
                "uid" to go.uId,
                "name" to go.name
            )
        )
    }

    private fun buildHierarchyPath(go: GameObject): String {
        val path = mutableListOf<String>()
        var current: GameObject? = go

        while (current != null) {
            path.add(current.name)
            current = current.parent
        }

        return path.asReversed().joinToString(" > ")
    }

    private fun determineSubcategory(go: GameObject): String {
        return when {
            go.hasComponent<PointLightComponent>() -> "Light"
            go.hasComponent<AudioComponent>() -> "Audio Source"
            go.hasComponent<RenderComponent>() -> "Mesh"
            else -> stringManager.getString("search.category.gameobject.default")
        }
    }

    private fun determineIcon(go: GameObject): String {
        return when {
            go.hasComponent<PointLightComponent>() -> Icons.SUN
            go.hasComponent<AudioComponent>() -> Icons.MUSIC
            go.hasComponent<RenderComponent>() -> Icons.CUBE
            else -> Icons.CUBE
        }
    }
}
