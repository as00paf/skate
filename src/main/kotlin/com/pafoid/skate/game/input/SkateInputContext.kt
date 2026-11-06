package com.pafoid.skate.game.input

import com.pafoid.skate.engine.gameplay.ActionQuery
import com.pafoid.skate.engine.gameplay.InputContextRequest
import com.pafoid.skate.engine.input.ContextBlocking

/**
 * The `skate` input context while the skater rides (INP-10 §7): above `gameplay` (priority 10) and consuming the
 * sources it binds, so Shift/Ctrl/Space (LT/LB/A) push, manual and ollie instead of sprinting, crouching and jumping.
 * Off the board nothing is held and the same keys are the character's again. The behavior asks for it every frame
 * through [ActionQuery.holdContexts]; the engine pushes and pops, and releases it when play stops.
 */
object SkateInputContext {
    const val PRIORITY = 10

    val REQUEST = InputContextRequest(SkateActions.CONTEXT, PRIORITY, ContextBlocking.CONSUME_BOUND)

    private val riding = listOf(REQUEST)

    fun wanted(mounted: Boolean): List<InputContextRequest> = if (mounted) riding else emptyList()

    /** Holds the skate context through [actions] when [mounted], releases it otherwise. */
    fun sync(actions: ActionQuery, mounted: Boolean) = actions.holdContexts(wanted(mounted))
}
