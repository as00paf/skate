package com.pafoid.skate.game.input

import com.pafoid.skate.engine.gameplay.ActionQuery
import com.pafoid.skate.engine.gameplay.InputContextRequest

/**
 * Scripted [ActionQuery] for behavior tests: [down] actions are held, [pressed] went down this frame, [values]
 * gives analog actions (sticks, triggers).
 */
class FakeActions(
    var down: Set<String> = emptySet(),
    var pressed: Set<String> = emptySet(),
    var values: Map<String, Float> = emptyMap(),
) : ActionQuery {
    /** What the behavior last asked to hold. */
    var held: List<InputContextRequest> = emptyList()
        private set

    override fun isDown(action: String) = action in down || action in pressed
    override fun wasPressed(action: String) = action in pressed
    override fun value(action: String) = values[action] ?: if (isDown(action)) 1f else 0f
    override fun holdContexts(contexts: List<InputContextRequest>) {
        held = contexts
    }

    companion object {
        /** The skate actions a rider holds this frame. */
        fun skate(push: Boolean = false, manual: Boolean = false, ollie: Boolean = false) = FakeActions(
            down = setOfNotNull(SkateActions.PUSH.takeIf { push }, SkateActions.MANUAL.takeIf { manual }),
            pressed = setOfNotNull(SkateActions.OLLIE.takeIf { ollie }),
        )

        /** Trick stick at ([x], [y]) and front foot at ([fx], [fy]). */
        fun sticks(x: Float = 0f, y: Float = 0f, fx: Float = 0f, fy: Float = 0f) = FakeActions(
            values = mapOf(SkateActions.TRICK_X to x, SkateActions.TRICK_Y to y, SkateActions.FRONT_X to fx, SkateActions.FRONT_Y to fy),
        )
    }
}
