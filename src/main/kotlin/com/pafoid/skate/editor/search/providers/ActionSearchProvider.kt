package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.game.prefabs.PrefabType
import com.pafoid.skate.editor.events.ViewportAction.CreateEmpty
import com.pafoid.skate.editor.events.ViewportAction.CreateLight
import com.pafoid.skate.editor.events.ViewportAction.Delete
import com.pafoid.skate.editor.events.ViewportAction.Duplicate
import com.pafoid.skate.editor.events.ViewportAction.RenameGameObject
import com.pafoid.skate.editor.events.ViewportAction.ResetTransform
import com.pafoid.skate.editor.events.ViewportAction.SpawnPrefab
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.search.BaseSearchProvider
import com.pafoid.skate.editor.search.data.EditorAction
import com.pafoid.skate.editor.search.data.SearchCategory
import com.pafoid.skate.editor.search.data.SearchResult
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.render.data.LightType

class ActionSearchProvider(
    val engine: Engine
) : BaseSearchProvider() {
    private val sceneManager = engine.sceneManager
    private val stringManager = engine.stringManager
    private val logger = engine.logger
    private val eventSystem = engine.eventSystem

    override val category: SearchCategory = SearchCategory.ACTION

    private val actions = listOf(
        EditorAction(
            actionId = "create_empty",
            displayNameKey = "search.action.create_empty",
            keywords = listOf("create", "empty", "gameobject", "new", "add"),
            descriptionKey = "search.action.create_empty.desc",
            icon = Icons.PLUS,
            execute = { createEmptyGameObject() }
        ),
        EditorAction(
            actionId = "save_scene",
            displayNameKey = "search.action.save_scene",
            keywords = listOf("save", "scene", "disk", "write", "store"),
            descriptionKey = "search.action.save_scene.desc",
            icon = Icons.SAVE,
            execute = { saveScene() }
        ),
        EditorAction(
            actionId = "play",
            displayNameKey = "search.action.play",
            keywords = listOf("play", "start", "run", "simulate", "simulation"),
            descriptionKey = "search.action.play.desc",
            icon = Icons.PLAY,
            execute = { startSimulation() }
        ),
        EditorAction(
            actionId = "stop",
            displayNameKey = "search.action.stop",
            keywords = listOf("stop", "pause", "end", "halt", "simulation"),
            descriptionKey = "search.action.stop.desc",
            icon = Icons.STOP,
            execute = { stopSimulation() }
        ),
        EditorAction(
            actionId = "reset_transform",
            displayNameKey = "search.action.reset_transform",
            keywords = listOf("reset", "transform", "position", "rotation", "scale", "identity", "zero"),
            descriptionKey = "search.action.reset_transform.desc",
            icon = Icons.ARROW_ROTATE,
            execute = { resetTransform() }
        ),
        EditorAction(
            actionId = "delete",
            displayNameKey = "search.action.delete",
            keywords = listOf("delete", "remove", "destroy", "trash", "kill"),
            descriptionKey = "search.action.delete.desc",
            icon = Icons.TRASH,
            execute = { deleteSelected() }
        ),
        EditorAction(
            actionId = "duplicate",
            displayNameKey = "search.action.duplicate",
            keywords = listOf("duplicate", "copy", "clone", "replicate"),
            descriptionKey = "search.action.duplicate.desc",
            icon = Icons.COPY,
            execute = { duplicateSelected() }
        ),
        EditorAction(
            actionId = "rename_scene",
            displayNameKey = "search.action.rename_scene",
            keywords = listOf("rename", "scene", "name", "title"),
            descriptionKey = "search.action.rename_scene.desc",
            icon = Icons.EDIT,
            execute = { renameScene() }
        ),
        EditorAction(
            actionId = "save_scene_as",
            displayNameKey = "search.action.save_scene_as",
            keywords = listOf("save", "as", "scene", "export", "copy"),
            descriptionKey = "search.action.save_scene_as.desc",
            icon = Icons.FOLDER_OPEN,
            execute = { saveSceneAs() }
        ),
        EditorAction(
            actionId = "close_scene",
            displayNameKey = "search.action.close_scene",
            keywords = listOf("close", "scene", "remove", "exit"),
            descriptionKey = "search.action.close_scene.desc",
            icon = Icons.TRASH,
            execute = { closeScene() }
        ),
        EditorAction(
            actionId = "close_other_scenes",
            displayNameKey = "search.action.close_other_scenes",
            keywords = listOf("close", "others", "scenes", "remove"),
            descriptionKey = "search.action.close_other_scenes.desc",
            icon = Icons.TRASH,
            execute = { closeOtherScenes() }
        ),
        EditorAction(
            actionId = "create_scene",
            displayNameKey = "search.action.create_scene",
            keywords = listOf("create", "new", "scene", "add", "empty"),
            descriptionKey = "search.action.create_scene.desc",
            icon = Icons.PLUS,
            execute = { createScene() }
        )/*,
        EditorAction(
            actionId = "create_primitive",
            displayNameKey = "search.action.create_primitive",
            keywords = listOf("create", "primitive", "cube", "sphere", "box", "cylinder", "plane", "3d", "object"),
            descriptionKey = "search.action.create_primitive.desc",
            icon = Icons.CUBE,
            execute = { createPrimitive() }
        )*/,
        EditorAction(
            actionId = "create_light",
            displayNameKey = "search.action.create_light",
            keywords = listOf("create", "light", "directional", "point", "spot", "lamp"),
            descriptionKey = "search.action.create_light.desc",
            icon = Icons.SUN,
            execute = { createLight() }
        ),
        EditorAction(
            actionId = "spawn_prefab",
            displayNameKey = "search.action.spawn_prefab",
            keywords = listOf("spawn", "prefab", "ledge", "rail", "kicker", "ramp", "obstacle"),
            descriptionKey = "search.action.spawn_prefab.desc",
            icon = Icons.GEAR,
            execute = { spawnPrefab() }
        ),
        EditorAction(
            actionId = "rename_gameobject",
            displayNameKey = "search.action.rename_gameobject",
            keywords = listOf("rename", "object", "gameobject", "name", "title"),
            descriptionKey = "search.action.rename_gameobject.desc",
            icon = Icons.EDIT,
            execute = { renameGameObject() }
        ),
        EditorAction(
            actionId = "delete_scene",
            displayNameKey = "search.action.delete_scene",
            keywords = listOf("delete", "scene", "remove", "destroy", "file"),
            descriptionKey = "search.action.delete_scene.desc",
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

    private fun calculateActionScore(action: EditorAction, query: String): Float {
        var bestScore = 0.0f

        val displayName = stringManager.getString(action.displayNameKey)
        val displayNameScore = calculateRelevance(displayName, query)
        bestScore = maxOf(bestScore, displayNameScore)

        for (keyword in action.keywords) {
            val keywordScore = calculateRelevance(keyword, query)
            bestScore = maxOf(bestScore, keywordScore * 0.9f) // Keywords score slightly lower
        }

        // Try fuzzy match on display name if no exact match found
        if (bestScore == 0.0f) {
            bestScore = fuzzyMatch(displayName, query)
        }

        return bestScore
    }

    private fun createSearchResult(action: EditorAction, score: Float): SearchResult {
        return SearchResult(
            id = "action_${action.actionId}",
            displayName = stringManager.getString(action.displayNameKey),
            category = SearchCategory.ACTION,
            subcategory = stringManager.getString("search.action.subcategory"),
            description = stringManager.getString(action.descriptionKey),
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
        eventSystem.publish(EngineAction.SetRuntimePlaying(true))
        logger.logEditor("Simulation start requested")
    }

    private fun stopSimulation() {
        eventSystem.publish(EngineAction.SetRuntimePlaying(false))
        eventSystem.publish(com.pafoid.skate.editor.events.ViewportAction.StopSimulation)
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

    private fun createPrimitive() {// TODO: fix
        //eventSystem.publish(CreatePrimitive("Cube", Vector3f(0.5f, 0.5f, 0.5f)))
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
