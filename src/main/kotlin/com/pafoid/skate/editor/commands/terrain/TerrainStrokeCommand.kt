package com.pafoid.skate.editor.commands.terrain

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.KeyedCommand
import com.pafoid.skate.editor.terrain.TerrainBrush
import com.pafoid.skate.editor.terrain.TerrainEditRecorder.Snapshot
import com.pafoid.skate.engine.ecs.components.TerrainComponent

/**
 * One sculpt or paint stroke (EDT-92 §3): the touched chunks' heights and painted biome ids before and after. The
 * stroke is applied live while the mouse drags, so the command is pushed already done ([execute] is redo). Undo and
 * redo write the chunks back, recompute splat/biomes/scatter and save the terrain asset ([save]).
 */
class TerrainStrokeCommand(
    private val terrain: TerrainComponent,
    private val before: Map<Pair<Int, Int>, Snapshot>,
    private val after: Map<Pair<Int, Int>, Snapshot>,
    private val painted: Boolean,
    private val save: (TerrainComponent) -> Unit,
) : Command, KeyedCommand {
    override val displayNameKey: String = "command.TerrainStrokeCommand." + if (painted) "paint" else "sculpt"

    val chunkCount: Int get() = before.size

    override fun execute() {
        TerrainBrush.restore(terrain.runtime, after)
        save(terrain)
    }

    override fun undo() {
        TerrainBrush.restore(terrain.runtime, before)
        save(terrain)
    }

    override fun getDisplayName(): String = if (painted) "Paint Terrain" else "Sculpt Terrain"
    override fun getTargetName(): String? = terrain.gameObject?.name
}
