package com.pafoid.skate.engine.gameplay

/**
 * Minimal deterministic state machine. Each [update] takes at most one transition: the matching one with
 * the highest priority (first registered wins ties). Has no ECS, window or GL dependencies.
 */
class StateMachine<S : Any, C : Any>(initial: S) {

    private class Transition<S, C>(val from: S?, val to: S, val priority: Int, val guard: (C) -> Boolean)

    private val transitions = mutableListOf<Transition<S, C>>()
    private val enterHooks = mutableMapOf<S, (C) -> Unit>()
    private val exitHooks = mutableMapOf<S, (C) -> Unit>()

    var current: S = initial
        private set

    /** Seconds spent in [current]. */
    var timeInState = 0f
        private set

    /** The state left by the last transition, or null if none happened yet. */
    var previous: S? = null
        private set

    /** Registers a transition. [from] = null means "from any state". */
    fun transition(from: S?, to: S, priority: Int = 0, guard: (C) -> Boolean): StateMachine<S, C> {
        transitions += Transition(from, to, priority, guard)
        return this
    }

    fun onEnter(state: S, hook: (C) -> Unit): StateMachine<S, C> {
        enterHooks[state] = hook
        return this
    }

    fun onExit(state: S, hook: (C) -> Unit): StateMachine<S, C> {
        exitHooks[state] = hook
        return this
    }

    /** Advances time and evaluates transitions. Returns true when the state changed. */
    fun update(context: C, dt: Float): Boolean {
        timeInState += dt
        var best: Transition<S, C>? = null
        for (t in transitions) {
            if (t.from != null && t.from != current) continue
            if (t.to == current) continue
            if (best != null && t.priority <= best.priority) continue
            if (t.guard(context)) best = t
        }
        val chosen = best ?: return false
        moveTo(chosen.to, context)
        return true
    }

    /** Forces a state, running exit/enter hooks. */
    fun force(state: S, context: C) {
        if (state != current) moveTo(state, context)
    }

    private fun moveTo(state: S, context: C) {
        exitHooks[current]?.invoke(context)
        previous = current
        current = state
        timeInState = 0f
        enterHooks[state]?.invoke(context)
    }
}
