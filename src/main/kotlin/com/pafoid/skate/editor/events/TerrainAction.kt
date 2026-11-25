package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainStamp

/** Terrain inspector and context-menu edits (EDT-92), turned into undoable commands by TerrainActionHandler. */
sealed class TerrainAction(eventName: String) : Event(eventName) {
    data class ApplyPreset(val terrain: TerrainComponent, val preset: TerrainPreset) : TerrainAction("terrain.apply_preset")
    data class SetStamps(val terrain: TerrainComponent, val stamps: List<TerrainStamp>) : TerrainAction("terrain.set_stamps")
    data class CreateAsset(val terrain: TerrainComponent) : TerrainAction("terrain.create_asset")
    /** Adds a FLATTEN stamp under [target]'s bounds to the terrain beneath it. */
    data class FlattenUnder(val target: GameObject) : TerrainAction("terrain.flatten_under")
}
