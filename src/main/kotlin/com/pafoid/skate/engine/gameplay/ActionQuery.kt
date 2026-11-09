package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputReader

/** An input context a character wants on the stack while some state lasts (riding, driving, aiming...). */
data class InputContextRequest(
    val context: InputContext,
    val priority: Int = 0,
    val blocking: ContextBlocking = ContextBlocking.CONSUME_BOUND,
)

/**
 * This frame's actions as a character behavior sees them (INP-10), in [CharacterInput.actions]. Behaviors read their
 * own actions here (a game's ride or vehicle actions) instead of reinterpreting the generic character fields, and say
 * which input contexts they need with [holdContexts]. [NONE] reads nothing and holds nothing (tests, replays).
 */
interface ActionQuery {
    fun isDown(action: String): Boolean
    /** Edge: the action went down this frame. */
    fun wasPressed(action: String): Boolean
    fun value(action: String): Float

    /**
     * Makes [contexts] exactly the contexts this character holds on the input stack, from now on (the same frame
     * included). Call it every frame with the current wish; an unchanged list costs nothing. The engine releases
     * them when play stops or the scene changes, so a behavior never has to clean up.
     */
    fun holdContexts(contexts: List<InputContextRequest>)

    companion object {
        val NONE: ActionQuery = object : ActionQuery {
            override fun isDown(action: String) = false
            override fun wasPressed(action: String) = false
            override fun value(action: String) = 0f
            override fun holdContexts(contexts: List<InputContextRequest>) {}
        }

        /** Reads [reader]; contexts go through [hold] (null: they are ignored). */
        fun of(reader: InputReader, hold: ((List<InputContextRequest>) -> Unit)? = null): ActionQuery = object : ActionQuery {
            override fun isDown(action: String) = reader.isActionDown(action)
            override fun wasPressed(action: String) = reader.wasActionPressed(action)
            override fun value(action: String) = reader.actionValue(action)
            override fun holdContexts(contexts: List<InputContextRequest>) { hold?.invoke(contexts) }
        }
    }
}
