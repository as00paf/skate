package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.data.EditorAction
import com.pafoid.skate.editor.data.PrefabType
import com.pafoid.skate.editor.events.SceneAction
import com.pafoid.skate.editor.events.SceneAction.OpenRequested
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.editor.events.ViewportAction.*
import com.pafoid.skate.engine.render.data.LightType
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Search provider for editor actions and commands.
 *
 * This provider searches a hardcoded list of editor actions by display name and keywords.
 * Results support direct execution of the action when selected.
 */
class ActionSearchProvider(
    private val sceneManager: SceneManager,
    private val logger: LoggerService,
) : BaseSearchProvider(), KoinComponent {
    
    private val eventSystem: EventSystem by inject()

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
        ),
        EditorAction(
            actionId = "rename_scene",
            displayName = "Rename Scene",
            keywords = listOf("rename", "scene", "name", "title"),
            description = "Rename the current scene",
            icon = Icons.EDIT,
            execute = { renameScene() }
        ),
        EditorAction(
            actionId = "save_scene_as",
            displayName = "Save Scene As",
            keywords = listOf("save", "as", "scene", "export", "copy"),
            description = "Save the current scene to a new file",
            icon = Icons.FOLDER_OPEN,
            execute = { saveSceneAs() }
        ),
        EditorAction(
            actionId = "close_scene",
            displayName = "Close Scene",
            keywords = listOf("close", "scene", "remove", "exit"),
            description = "Close the current scene",
            icon = Icons.TRASH,
            execute = { closeScene() }
        ),
        EditorAction(
            actionId = "close_other_scenes",
            displayName = "Close Other Scenes",
            keywords = listOf("close", "others", "scenes", "remove"),
            description = "Close all scenes except the current one",
            icon = Icons.TRASH,
            execute = { closeOtherScenes() }
        ),
        EditorAction(
            actionId = "create_scene",
            displayName = "Create Scene",
            keywords = listOf("create", "new", "scene", "add", "empty"),
            description = "Create a new empty scene",
            icon = Icons.PLUS,
            execute = { createScene() }
        ),
        EditorAction(
            actionId = "create_primitive",
            displayName = "Create Primitive",
            keywords = listOf("create", "primitive", "cube", "sphere", "box", "cylinder", "plane", "3d", "object"),
            description = "Create a 3D primitive object",
            icon = Icons.CUBE,
            execute = { createPrimitive() }
        ),
        EditorAction(
            actionId = "create_light",
            displayName = "Create Light",
            keywords = listOf("create", "light", "directional", "point", "spot", "lamp"),
            description = "Create a light object",
            icon = Icons.SUN,
            execute = { createLight() }
        ),
        EditorAction(
            actionId = "spawn_prefab",
            displayName = "Spawn Prefab",
            keywords = listOf("spawn", "prefab", "ledge", "rail", "kicker", "ramp", "obstacle"),
            description = "Spawn a prefab obstacle",
            icon = Icons.GEAR,
            execute = { spawnPrefab() }
        ),
        EditorAction(
            actionId = "open_scene",
            displayName = "Open Scene",
            keywords = listOf("open", "load", "scene", "file", "read"),
            description = "Open a scene from file",
            icon = Icons.FOLDER_OPEN,
            execute = { openScene() }
        ),
        EditorAction(
            actionId = "rename_gameobject",
            displayName = "Rename GameObject",
            keywords = listOf("rename", "object", "gameobject", "name", "title"),
            description = "Rename the selected GameObject",
            icon = Icons.EDIT,
            execute = { renameGameObject() }
        ),
        EditorAction(
            actionId = "delete_scene",
            displayName = "Delete Scene",
            keywords = listOf("delete", "scene", "remove", "destroy", "file"),
            description = "Delete the current scene and its file",
            icon = Icons.TRASH,
            execute = { deleteScene() }
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
        eventSystem.publish(CreateEmpty(scene))
        logger.logEditor("Create empty GameObject requested")
    }

    private fun saveScene() {
        val scene = sceneManager.currentScene ?: return
        eventSystem.publish(SceneAction.SaveRequested(scene))
        logger.logEditor("Save scene requested")
    }

    private fun startSimulation() {
        eventSystem.publish(SetRuntimePlaying(true))
        logger.logEditor("Simulation start requested")
    }

    private fun stopSimulation() {
        eventSystem.publish(SetRuntimePlaying(false))
        logger.logEditor("Simulation stop requested")
    }

    private fun resetTransform() {
        val selected = sceneManager.currentScene?.selectedGameObject ?: return
        eventSystem.publish(ResetTransform(selected))
        logger.logEditor("Reset transform requested for: ${selected.name}")
    }

    private fun deleteSelected() {
        val scene = sceneManager.currentScene ?: return
        val selected = scene.selectedGameObject ?: return
        eventSystem.publish(Delete(selected, scene))
        logger.logEditor("Delete GameObject requested: ${selected.name}")
    }

    private fun duplicateSelected() {
        val selected = sceneManager.currentScene?.selectedGameObject ?: return
        eventSystem.publish(Duplicate(selected))
        logger.logEditor("Duplicate GameObject requested: ${selected.name}")
    }

    // Scene-related actions
    private fun renameScene() {
        val scene = sceneManager.currentScene ?: return
        // Publish event to trigger SceneActionHandler which will show rename UI
        eventSystem.publish(SceneAction.RenameRequested(scene, scene.name))
        logger.logEditor("Scene rename requested")
    }

    private fun saveSceneAs() {
        val scene = sceneManager.currentScene ?: return
        eventSystem.publish(SceneAction.SaveAsRequested(scene))
        logger.logEditor("Save scene as requested")
    }

    private fun closeScene() {
        val scene = sceneManager.currentScene ?: return
        if (sceneManager.openScenes.size <= 1) return
        eventSystem.publish(SceneAction.CloseRequested(scene))
        logger.logEditor("Close scene requested")
    }

    private fun closeOtherScenes() {
        val scene = sceneManager.currentScene ?: return
        eventSystem.publish(SceneAction.CloseOthersRequested(scene))
        logger.logEditor("Close other scenes requested")
    }

    private fun createScene() {
        eventSystem.publish(SceneAction.CreateRequested)
        logger.logEditor("Create scene requested")
    }

    private fun createPrimitive() {
        eventSystem.publish(CreatePrimitive("Cube", Vector3f(0.5f, 0.5f, 0.5f)))
        logger.logEditor("Create primitive executed")
    }

    private fun createLight() {
        eventSystem.publish(CreateLight("DirectionalLight", LightType.DIRECTIONAL))
        logger.logEditor("Create light executed")
    }

    private fun spawnPrefab() {
        eventSystem.publish(SpawnPrefab(PrefabType.LEDGE))
        logger.logEditor("Spawn prefab executed")
    }

    private fun openScene() {
        eventSystem.publish(OpenRequested)
        logger.logEditor("Open scene requested")
    }

    private fun renameGameObject() {
        val scene = sceneManager.currentScene ?: return
        val selected = scene.selectedGameObject ?: return
        val newName = javax.swing.JOptionPane.showInputDialog(
            null,
            "Enter new name:",
            selected.name
        )
        if (!newName.isNullOrBlank() && newName != selected.name) {
            eventSystem.publish(RenameGameObject(selected, newName))
            logger.logEditor("GameObject rename requested: '${selected.name}' -> '$newName'")
        }
    }

    private fun deleteScene() {
        val scene = sceneManager.currentScene ?: return
        if (sceneManager.openScenes.size <= 1) return
        eventSystem.publish(SceneAction.DeleteRequested(scene))
        logger.logEditor("Delete scene executed")
    }
}
