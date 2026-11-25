package com.pafoid.skate.editor.terrain

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.terrain.CreateTerrainAssetCommand
import com.pafoid.skate.engine.assets.terrain.TerrainAssetStore
import com.pafoid.skate.engine.assets.terrain.TerrainAssets
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import java.io.File

/**
 * Terrain asset plumbing for the terrain tools (EDT-92 §5): creates the `.skterrain` asset a terrain needs before it
 * can keep sculpted or painted data, and saves it after strokes. Built by TerrainActionHandler with the project's
 * folders; [active] is what `TerrainBrushTool` uses.
 */
class TerrainEditing(
    /** `<project>/Assets/Data`, or null when no project is open. */
    private val dataDir: () -> File?,
    /** Registers a new asset folder in the project's registry, so its GUID resolves at once. */
    private val register: (File) -> Unit,
    /** Runs a command through the undo manager. */
    private val run: (Command) -> Unit,
    /** Tells the user an asset was created (status-bar toast). */
    private val created: (TerrainComponent, File) -> Unit = { _, _ -> },
    private val saveAsset: (TerrainComponent) -> Unit = { TerrainAssets.save(it) },
    private val onError: (String) -> Unit = {},
) {
    /** Saves [terrain]'s asset (no-op without one). */
    fun save(terrain: TerrainComponent) {
        if (terrain.asset.isBlank()) return
        runCatching { saveAsset(terrain) }.onFailure { onError("Terrain asset save failed: ${it.message}") }
    }

    /** Creates `<Assets/Data>/<entity name>.skterrain` for [terrain] and registers it; null without a project. */
    fun createAsset(terrain: TerrainComponent): File? {
        val dir = dataDir() ?: return null
        dir.mkdirs()
        val name = assetName(terrain.gameObject?.name ?: "") { File(dir, "$it.${TerrainAssetStore.EXTENSION}").exists() }
        val folder = TerrainAssetStore.create(dir, name, terrain)
        register(folder)
        return folder
    }

    /**
     * Makes sure [terrain] has an asset before a stroke (approved Q2: created automatically on the first stroke, as
     * its own execute-only command). Returns true when the terrain has an asset afterwards.
     */
    fun ensureAsset(terrain: TerrainComponent): Boolean {
        if (terrain.asset.isNotBlank()) return true
        val command = CreateTerrainAssetCommand(terrain, ::createAsset)
        run(command)
        command.folder?.let { created(terrain, it) }
        return terrain.asset.isNotBlank()
    }

    companion object {
        @Volatile
        var active: TerrainEditing? = null

        /** Enabled terrains in [root]'s tree, [root] included. */
        fun terrainsIn(root: com.pafoid.skate.engine.ecs.GameObject): List<TerrainComponent> {
            val out = ArrayList<TerrainComponent>()
            fun walk(go: com.pafoid.skate.engine.ecs.GameObject) {
                go.components.filterIsInstance<TerrainComponent>().firstOrNull()?.takeIf { it.enabled }?.let { out += it }
                go.children.forEach(::walk)
            }
            walk(root)
            return out
        }

        /** A file-safe asset name from [entityName], made unique with `_2`, `_3`… while [exists] says it is taken. */
        fun assetName(entityName: String, exists: (String) -> Boolean): String {
            val base = entityName.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "Terrain" }
            if (!exists(base)) return base
            var n = 2
            while (exists("${base}_$n")) n++
            return "${base}_$n"
        }
    }
}
