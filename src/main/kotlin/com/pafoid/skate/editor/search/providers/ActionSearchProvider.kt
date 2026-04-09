package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.commands.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.DeleteGameObjectCommand
import com.pafoid.skate.editor.commands.TransformCommand
import com.pafoid.skate.editor.data.EditorAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.SearchCategory
import com.pafoid.skate.editor.search.SearchResult
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.game.level.LevelManager
import org.koin.core.component.KoinComponent

/**
 * Search provider for editor actions and commands.
 *
 * This provider searches a hardcoded list of editor actions by display name and keywords.
 * Results support direct execution of the action when selected.
 */
class ActionSearchProvider(
    private val sceneManager: SceneManager,
    private val levelManager: LevelManager,
    private val undoRedoManager: UndoRedoManager,
    private val serializer: Serializer,
    private val logger: LoggerService,
) : BaseSearchProvider(), KoinComponent {

    override val category: SearchCategory = SearchCategory.ACTION

    private val actions = listOf(
        EditorAction(
            actionId = "create_empty",
            displayName = "Create Empty",
            keywords = listOf("create", "empty", "gameobject", "new", "add"),
            description = "Create a new empty GameObject",
            icon = Icons.PLUS,
            execute = { createEmptyGameObject() }
        ),
        EditorAction(
            actionId = "save_scene",
            displayName = "Save Scene",
            keywords = listOf("save", "scene", "disk", "write", "store"),
            description = "Save the current scene to disk",
            icon = Icons.SAVE,
            execute = { saveScene() }
        ),
        EditorAction(
            actionId = "play",
            displayName = "Play",
            keywords = listOf("play", "start", "run", "simulate", "simulation"),
            description = "Start the simulation",
            icon = Icons.PLAY,
            execute = { startSimulation() }
        ),
        EditorAction(
            actionId = "stop",
            displayName = "Stop",
            keywords = listOf("stop", "pause", "end", "halt", "simulation"),
            description = "Stop the simulation",
            icon = Icons.STOP,
            execute = { stopSimulation() }
        ),
        EditorAction(
            actionId = "reset_transform",
            displayName = "Reset Transform",
            keywords = listOf("reset", "transform", "position", "rotation", "scale", "identity", "zero"),
            description = "Reset selected object's transform to identity",
            icon = Icons.ARROW_ROTATE,
            execute = { resetTransform() }
        ),
        EditorAction(
            actionId = "delete",
            displayName = "Delete",
            keywords = listOf("delete", "remove", "destroy", "trash", "kill"),
            description = "Delete the selected object",
            icon = Icons.TRASH,
            execute = { deleteSelected() }
        ),
        EditorAction(
            actionId = "duplicate",
            displayName = "Duplicate",
            keywords = listOf("duplicate", "copy", "clone", "replicate"),
            description = "Duplicate the selected object",
            icon = Icons.COPY,
            execute = { duplicateSelected() }
        )
    )

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        return actions
            .mapNotNull { action ->
                val score = calculateActionScore(action, query)
                if (score > 0.0f) {
                    createSearchResult(action, score)
                } else {
                    null
                }
            }
            .sortedByDescending { it.relevanceScore }
    }

    override fun navigate(result: SearchResult) {
        val actionId = result.metadata["actionId"] as? String ?: return
        val action = actions.find { it.actionId == actionId } ?: return
        action.execute()
    }

    /**
     * Calculates a relevance score for an action based on the query.
     *
     * Scores are calculated by matching against both the display name and keywords.
     * The highest score from any match is used.
     *
     * @param action The action to score
     * @param query The search query
     * @return A relevance score from 0.0f to 1.0f
     */
    private fun calculateActionScore(action: EditorAction, query: String): Float {
        var bestScore = 0.0f

        val displayNameScore = calculateRelevance(action.displayName, query)
        bestScore = maxOf(bestScore, displayNameScore)

        for (keyword in action.keywords) {
            val keywordScore = calculateRelevance(keyword, query)
            bestScore = maxOf(bestScore, keywordScore * 0.9f) // Keywords score slightly lower
        }

        // Try fuzzy match on display name if no exact match found
        if (bestScore == 0.0f) {
            bestScore = fuzzyMatch(action.displayName, query)
        }

        return bestScore
    }

    /**
     * Creates a SearchResult from an EditorAction with the given relevance score.
     *
     * @param action The action to create a result for
     * @param score The relevance score calculated during search
     * @return A SearchResult with all necessary display and navigation data
     */
    private fun createSearchResult(action: EditorAction, score: Float): SearchResult {
        return SearchResult(
            id = "action_${action.actionId}",
            displayName = action.displayName,
            category = SearchCategory.ACTION,
            subcategory = "Editor Action",
            description = action.description,
            icon = action.icon,
            relevanceScore = score,
            metadata = mapOf(
                "actionId" to action.actionId
            )
        )
    }

    private fun createEmptyGameObject() {
        val scene = sceneManager.currentScene ?: return
        val newGameObject = GameObject("Empty GameObject")
        newGameObject.addComponent(Transform())
        undoRedoManager.executeCommand(CreateGameObjectCommand(newGameObject, scene))
        logger.logEditor("Created empty GameObject: ${newGameObject.name}")
    }

    private fun saveScene() {
        val scene = sceneManager.currentScene ?: return
        levelManager.save(scene)
    }

    private fun startSimulation() {
        val scene = sceneManager.currentScene ?: return
        if (!scene.isRunning) {
            scene.isRunning = true
            logger.logEditor("Simulation started")
        }
    }

    private fun stopSimulation() {
        val scene = sceneManager.currentScene ?: return
        if (scene.isRunning) {
            scene.isRunning = false
            logger.logEditor("Simulation stopped")
        }
    }

    private fun resetTransform() {
        val scene = sceneManager.currentScene ?: return
        val selected = scene.getSelectedGameObject() ?: return
        val transform = selected.getComponent<Transform>() ?: return

        val oldTransform = Transform().apply { copyFrom(transform) }
        val newTransform = Transform()
        newTransform.translation.set(0f, 0f, 0f)
        newTransform.rotation.set(0f, 0f, 0f)
        newTransform.scale.set(1f, 1f, 1f)

        undoRedoManager.executeCommand(TransformCommand(selected, oldTransform, newTransform))
        logger.logEditor("Reset transform for: ${selected.name}")
    }

    private fun deleteSelected() {
        val scene = sceneManager.currentScene ?: return
        val selected = scene.getSelectedGameObject() ?: return
        undoRedoManager.executeCommand(DeleteGameObjectCommand(selected, scene))
        logger.logEditor("Deleted GameObject: ${selected.name}")
    }

    private fun duplicateSelected() {
        val scene = sceneManager.currentScene ?: return
        val selected = scene.getSelectedGameObject() ?: return

        val duplicated = selected.copy(serializer)
        duplicated.name = "${selected.name} (Copy)"

        duplicated.getComponent<Transform>()?.translation?.add(1f, 0f, 0f)

        undoRedoManager.executeCommand(CreateGameObjectCommand(duplicated, scene))
        logger.logEditor("Duplicated GameObject: ${selected.name} -> ${duplicated.name}")
    }
}

