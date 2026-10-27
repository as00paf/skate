package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.input.DeadZoneSettings

sealed class InputSettingsAction(eventName: String) : Event(eventName) {
    /** [before] and [after] are snapshots; the handler applies [after] to the project's live settings. */
    data class SetDeadZones(val before: DeadZoneSettings, val after: DeadZoneSettings, val label: String) :
        InputSettingsAction("input_settings.set_dead_zones")
}
