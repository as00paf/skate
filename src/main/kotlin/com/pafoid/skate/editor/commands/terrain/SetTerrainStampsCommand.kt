package com.pafoid.skate.editor.commands.terrain

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.terrain.TerrainStamp

/**
 * Replaces a terrain's stamp list (EDT-92 §4): add, remove, a field edit or a handle drag. Stamps are immutable data,
 * so the lists are the whole state. The terrain rebuilds from its new key (after `TerrainSystem`'s debounce).
 */
class SetTerrainStampsCommand(
    private val terrain: TerrainComponent,
    after: List<TerrainStamp>,
) : Command {
    private val before: List<TerrainStamp> = terrain.stamps.toList()
    private val after: List<TerrainStamp> = after.toList()

    val isNoOp: Boolean get() = before == after

    override fun execute() = set(after)
    override fun undo() = set(before)

    private fun set(stamps: List<TerrainStamp>) {
        terrain.stamps.clear()
        terrain.stamps.addAll(stamps)
    }

    override fun getDisplayName(): String = "Edit Terrain Stamps"
    override fun getTargetName(): String? = terrain.gameObject?.name
}
