package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.objects.AudioComponentSettings
import com.pafoid.skate.editor.commands.objects.ReverbZoneSettings
import com.pafoid.skate.editor.settings.AudioSettings
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.ReverbZone
import com.pafoid.skate.engine.events.Event

sealed class AudioAction(eventName: String) : Event(eventName) {
    data class EditReverbZone(val zone: ReverbZone, val settings: ReverbZoneSettings) : AudioAction("audio.edit_reverb_zone")

    /** Replaces the project's mixer settings; undoable and saved with the project. */
    data class EditBuses(val settings: AudioSettings) : AudioAction("audio.edit_buses")

    /** Replaces the saved fields of an audio source; undoable. */
    data class EditComponent(val component: AudioComponent, val settings: AudioComponentSettings) : AudioAction("audio.edit_component")
}
