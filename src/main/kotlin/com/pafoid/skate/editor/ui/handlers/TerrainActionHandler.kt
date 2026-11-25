package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.terrain.ApplyTerrainPresetCommand
import com.pafoid.skate.editor.commands.terrain.CreateTerrainAssetCommand
import com.pafoid.skate.editor.commands.terrain.SetTerrainStampsCommand
import com.pafoid.skate.editor.events.TerrainAction
import com.pafoid.skate.editor.imgui.EditorToast
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.terrain.TerrainEditing
import com.pafoid.skate.editor.terrain.TerrainStampHandles
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.lighting.ModelBounds
import org.joml.Vector3f
import java.io.File

/**
 * Terrain editing (EDT-92): turns inspector and context-menu requests into undoable commands, owns the
 * [TerrainEditing] service the brush tool uses, and saves terrain assets when their scene is saved.
 */
class TerrainActionHandler(
    eventSystem: EventSystem,
    private val undoRedoManager: UndoRedoManager,
    private val projectManager: ProjectManager,
    private val sceneManager: SceneManager,
    private val strings: StringManager,
    private val logger: LoggerService,
) {
    val editing = TerrainEditing(
        dataDir = { projectManager.currentProject?.getProjectDirectory()?.absoluteFile?.let { File(it, "Assets/Data") } },
        register = { folder -> projectManager.assetRegistry()?.register(folder) },
        run = undoRedoManager::executeCommand,
        created = { _, folder -> EditorToast.show(strings.getString("component.TerrainComponent.asset.created", folder.name)) },
        onError = { logger.logEditor(it, LoggerService.LogLevel.ERROR) },
    )

    init {
        TerrainEditing.active = editing
        eventSystem.subscribe<TerrainAction.ApplyPreset> { e ->
            undoRedoManager.executeCommand(ApplyTerrainPresetCommand(e.terrain, e.preset))
        }
        eventSystem.subscribe<TerrainAction.SetStamps> { e ->
            val command = SetTerrainStampsCommand(e.terrain, e.stamps)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<TerrainAction.CreateAsset> { e ->
            val command = CreateTerrainAssetCommand(e.terrain, editing::createAsset)
            undoRedoManager.executeCommand(command)
            command.folder?.let { EditorToast.show(strings.getString("component.TerrainComponent.asset.created", it.name)) }
        }
        eventSystem.subscribe<TerrainAction.FlattenUnder> { e -> flattenUnder(e.target) }
        eventSystem.subscribe<SceneAction.Saved> { e -> TerrainEditing.terrainsIn(e.scene).forEach(editing::save) }
    }

    private fun flattenUnder(target: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val position = target.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f()) ?: return
        val terrain = terrainUnder(TerrainEditing.terrainsIn(scene), position) ?: return
        val origin = terrain.gameObject?.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f()) ?: Vector3f()
        val stamp = TerrainStampHandles.flattenUnder(boundsOf(target, position), origin) ?: return
        undoRedoManager.executeCommand(SetTerrainStampsCommand(terrain, terrain.stamps + stamp))
        logger.logEditor("Flattened terrain under ${target.name}")
    }

    companion object {
        /** The terrain whose square contains [world] (XZ), else the first one. */
        fun terrainUnder(terrains: List<TerrainComponent>, world: Vector3f): TerrainComponent? = terrains.firstOrNull { t ->
            val origin = t.gameObject?.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f()) ?: Vector3f()
            val half = (t.runtime.layout?.size ?: t.effectiveSize()) * 0.5f
            kotlin.math.abs(world.x - origin.x) <= half && kotlin.math.abs(world.z - origin.z) <= half
        } ?: terrains.firstOrNull()

        /** World bounding spheres of the meshes in [target]'s tree; a 0.5 m sphere at [position] when it has none. */
        fun boundsOf(target: GameObject, position: Vector3f): List<Pair<Vector3f, Float>> {
            val out = ArrayList<Pair<Vector3f, Float>>()
            fun walk(go: GameObject) {
                val model = go.getComponent<RenderComponent>()?.model
                val transform = go.getComponent<Transform>()
                if (model != null && transform != null) {
                    val c = Vector3f()
                    val r = ModelBounds.of(model).toWorld(transform.worldMatrix, c)
                    out += c to r
                }
                go.children.forEach(::walk)
            }
            walk(target)
            return out.ifEmpty { listOf(Vector3f(position) to 0.5f) }
        }
    }
}
