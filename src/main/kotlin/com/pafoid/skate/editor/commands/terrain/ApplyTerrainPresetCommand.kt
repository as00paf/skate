package com.pafoid.skate.editor.commands.terrain

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainRecipe

/** Replaces a terrain's recipe with a preset's (`TerrainComponent.applyPreset`, EDT-92 §1). */
class ApplyTerrainPresetCommand(private val terrain: TerrainComponent, private val preset: TerrainPreset) : Command {
    private val before: TerrainRecipe = terrain.recipe

    override fun execute() = terrain.applyPreset(preset)
    override fun undo() { terrain.recipe = before }

    override fun getDisplayName(): String = "Apply Terrain Preset"
    override fun getTargetName(): String? = terrain.gameObject?.name
}
