package com.pafoid.skate.editor.commands.terrain

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.terrain.TerrainBrush
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.terrain.BiomeSource

/**
 * Switches a terrain to painted biomes (EDT-92 §2) before its first paint stroke: every resident chunk starts from its
 * current dominant biomes, so nothing changes on screen, and the asset is saved so the rebuild that follows the
 * source change loads the painted ids. Undo goes back to the climate map and drops the painted ids.
 */
class UsePaintedBiomesCommand(
    private val terrain: TerrainComponent,
    private val save: (TerrainComponent) -> Unit,
) : Command {
    private val before: BiomeSource = terrain.biomeSource
    private val previous = terrain.runtime.chunks.associate { (it.cx to it.cz) to it.paintedBiomeIds?.copyOf() }

    val isNoOp: Boolean get() = before == BiomeSource.PAINTED

    override fun execute() {
        for (chunk in terrain.runtime.chunks) {
            if (chunk.paintedBiomeIds == null) chunk.paintedBiomeIds = TerrainBrush.seedPainted(chunk)
        }
        terrain.biomeSource = BiomeSource.PAINTED
        save(terrain)
    }

    override fun undo() {
        for (chunk in terrain.runtime.chunks) chunk.paintedBiomeIds = previous[chunk.cx to chunk.cz]?.copyOf()
        terrain.biomeSource = before
        save(terrain)
    }

    override fun getDisplayName(): String = "Use Painted Biomes"
    override fun getTargetName(): String? = terrain.gameObject?.name
}
