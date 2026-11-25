package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable

/**
 * Per-project input configuration: dead zones plus the user's action bindings.
 * Defaults come from the registered action definitions; [bindings] only stores customizations.
 */
@Serializable
class InputMappings(
    var deadZones: DeadZoneSettings = DeadZoneSettings(),
    var bindings: BindingProfile = BindingProfile(),
    /**
     * Project path of the project's actions asset (INP-11), e.g. [ProjectActionSet.DEFAULT_PATH]. Null means the
     * project defines no actions of its own (older projects); the editor sets it on the first project action.
     */
    var actionsPath: String? = null,
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
