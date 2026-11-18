package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.objects.WaterWaveSettings
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.events.Event

/** Edits from the water inspector (EDT-90); handled with undoable commands. */
sealed class WaterAction(eventName: String) : Event(eventName) {
    /** A preset, an added or removed wave, or a finished wave gesture: [before] is the state before it started. */
    data class EditWaves(
        val water: WaterComponent,
        val before: WaterWaveSettings,
        val after: WaterWaveSettings,
    ) : WaterAction("water.edit_waves")
}
