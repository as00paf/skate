package com.pafoid.skate.editor.commands.terrain

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.CommandCategory
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import java.io.File

/**
 * Creates `<dir>/<name>.skterrain` for a terrain and points the terrain at it (EDT-92 §5), through [create] (the
 * handler wraps `TerrainAssetStore.create` and registers the folder). Execute-only: the asset holds the sculpted data,
 * so undoing a later stroke never deletes it.
 */
class CreateTerrainAssetCommand(
    private val terrain: TerrainComponent,
    private val create: (TerrainComponent) -> File?,
) : ExecutionTrackedCommand {
    var folder: File? = null
        private set

    override fun execute() {
        if (terrain.asset.isNotBlank()) return
        folder = runCatching { create(terrain) }.getOrNull()
    }

    override fun undo() {}
    override fun wasSuccessful(): Boolean = folder != null
    override fun getCategory(): CommandCategory = CommandCategory.EXECUTE_ONLY
    override fun getDisplayName(): String = "Create Terrain Asset"
    override fun getTargetName(): String? = terrain.gameObject?.name
}
