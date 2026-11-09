package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.gameplay.camera.CameraCycle
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Switches the view between several scene cameras while the scene runs (GPL-22); `CameraCycleSystem` drives it.
 * Each press of the [actionId] input action, or click on the runtime UI button named [buttonName], makes the next
 * camera of [cameraNames] the active one. Put it on any object, typically the default camera.
 */
@Serializable
class CameraCycleComponent(
    /** Camera object names in cycle order, comma-separated (e.g. `Camera, FirstPersonCamera, FixedCamera`). */
    var cameraNames: String = "",
    /** Runtime UI button that advances the cycle; empty for none. */
    @GameObjectRef var buttonName: String = "",
    /** Input action that advances the cycle; empty for none. */
    var actionId: String = CameraCycle.ACTION,
) : Component() {
    /** Button clicks not yet applied (clicks arrive from the UI system, the switch happens in the cycle system). */
    @Transient @kotlin.jvm.Transient var pendingClicks = 0
}
