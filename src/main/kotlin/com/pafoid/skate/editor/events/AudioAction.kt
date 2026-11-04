package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.objects.ReverbZoneSettings
import com.pafoid.skate.editor.settings.AudioSettings
import com.pafoid.skate.engine.ecs.components.ReverbZone
import com.pafoid.skate.engine.events.Event

sealed class AudioAction(eventName: String) : Event(eventName) {
    data class EditReverbZone(val zone: ReverbZone, val settings: ReverbZoneSettings) : AudioAction("audio.edit_reverb_zone")

    /** Replaces the project's mixer settings; undoable and saved with the project. */
    data class EditBuses(val settings: AudioSettings) : AudioAction("audio.edit_buses")
}
