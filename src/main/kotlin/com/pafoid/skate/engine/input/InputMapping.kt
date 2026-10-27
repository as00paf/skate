package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable

/**
 * Per-project input configuration: dead zones plus the user's action bindings.
 * Defaults come from [DefaultActions]; [bindings] only stores customizations.
 */
@Serializable
class InputMappings(
    var deadZones: DeadZoneSettings = DeadZoneSettings(),
    var bindings: BindingProfile = BindingProfile(),
) {
    /** Copies the current customizations of [map] into this (persisted) configuration. */
    fun storeBindings(map: ActionMap, name: String = bindings.name) {
        bindings = map.toProfile(name)
    }

    fun reset() {
        deadZones = DeadZoneSettings()
        bindings = BindingProfile()
    }
}
