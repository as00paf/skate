package com.pafoid.skate.engine.gameplay.camera

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.listeners.GamepadConstants

/** Order and input of a [com.pafoid.skate.engine.ecs.components.CameraCycleComponent], without GL or a scene. */
object CameraCycle {

    /** Gameplay action that switches to the next camera (default: C, gamepad Back/View). */
    const val ACTION = "camera.cycle"

    /**
     * Adds [ACTION] to [map] when it is missing, the way `UiActions.bindDefaults` adds the UI tab actions, so the
     * input domain's own defaults stay untouched. Safe to call repeatedly; the user may rebind it afterwards.
     */
    fun bindDefaults(map: ActionMap) {
        if (map.definition(ACTION) != null) return
        map.define(ActionDefinition(ACTION, InputContext.GAMEPLAY, "Camera", defaults = listOf(
            ActionBinding.Key('C'.code), ActionBinding.GamepadButton(GamepadConstants.BUTTON_BACK))))
    }

    /** Camera object names of a comma-separated list, in cycle order (blanks dropped). */
    fun parseNames(list: String): List<String> = list.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Index of the camera after [current] in a cycle of [size] cameras. A camera outside the cycle (-1) goes to the
     * first one; an empty cycle gives -1.
     */
    fun next(current: Int, size: Int): Int = if (size <= 0) -1 else (current + 1).mod(size)
}
