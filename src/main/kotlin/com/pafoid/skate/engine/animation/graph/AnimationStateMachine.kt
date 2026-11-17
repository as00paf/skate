package com.pafoid.skate.engine.animation.graph

import kotlin.math.ceil
import kotlin.math.floor

/** An [AnimEventDef] that was crossed; [state] is the state that owns it. */
data class FiredEvent(val state: String, val name: String)

/**
 * Runtime instance of an [AnimationControllerAsset]. Pure: advance with [update], read [pose].
 * Crossfades are weight based; interrupting a fade freezes the currently blended pose as the new fade source.
 */
class AnimationStateMachine(
    private val asset: AnimationControllerAsset,
    motions: Map<String, Motion>,
    boneCount: Int,
    val parameters: AnimParameters = AnimParameters(),
) {
    // States without a clip play the rest pose (ANI-21 §4.4) unless the caller supplied one for "".
    private val motions: Map<String, Motion> =
        if (asset.states.any { it.motion.isEmpty() } && "" !in motions) motions + ("" to RestMotion()) else motions
    private val states = asset.states.associateBy { it.name }
    val pose = Pose(boneCount)
    private val fromPose = Pose(boneCount)
    private val toPose = Pose(boneCount)
    private val frozen = Pose(boneCount)

    var currentState: String = asset.defaultState
        private set
    var normalizedTime = 0f
        private set
    var nextState: String? = null
        private set
    private var nextTime = 0f
    private var fadeElapsed = 0f
    private var fadeDuration = 0f
    private var fadeFromFrozen = false

    // Unwrapped progress of the transition source this update, so exit times still fire when a loop wraps.
    private var sourceProgress = 0f

    val isTransitioning get() = nextState != null

    /** Progress of the running crossfade, 0..1 (0 when none). Read by the Animator window in play mode (ANI-21 §6). */
    val fadeProgress: Float get() = if (nextState == null || fadeDuration <= 0f) 0f else (fadeElapsed / fadeDuration).coerceIn(0f, 1f)

    /** The asset this machine runs. */
    val controller: AnimationControllerAsset get() = asset

    /** The motion [state] plays (for tools and tests). */
    fun motionOf(state: String): Motion = motions.getValue(states.getValue(state).motion)

    /**
     * Fade time in seconds into a state, by target state name, replacing the asset's transition durations. Gameplay
     * fills it from its character config so blend times are tuned there (GPL-06); states not listed keep the asset's.
     */
    val blendOverrides: MutableMap<String, Float> = HashMap()

    /** Events fired during the last [update], in firing order. */
    val firedEvents = mutableListOf<FiredEvent>()

    init {
        asset.states.forEach { require(it.motion in this.motions) { "No motion '${it.motion}' for state '${it.name}'" } }
        ControllerParameters.seed(asset, parameters)
        evaluate()
    }

    /** Back to the default state at its start, without a crossfade (inspector Reset, ANI-24). Parameters are kept. */
    fun reset() {
        currentState = asset.defaultState
        normalizedTime = 0f
        nextState = null
        nextTime = 0f
        fadeElapsed = 0f
        fadeDuration = 0f
        fadeFromFrozen = false
        sourceProgress = 0f
        firedEvents.clear()
        evaluate()
    }

    fun update(dt: Float) {
        firedEvents.clear()
        normalizedTime = advance(states.getValue(currentState), normalizedTime, dt)

        nextState?.let { ns ->
            nextTime = advance(states.getValue(ns), nextTime, dt)
            fadeElapsed += dt
            if (fadeElapsed >= fadeDuration) completeTransition()
        }

        findTransition()?.let { startTransition(it) }
        evaluate()
    }

    private fun advance(state: StateDef, t: Float, dt: Float): Float {
        val dur = motions.getValue(state.motion).duration(parameters)
        if (dur <= 0f) return t
        val raw = t + dt * state.speed / dur
        sourceProgress = raw
        fire(state, t, raw)
        return if (state.loop) raw - floor(raw) else raw.coerceIn(0f, 1f)
    }

    // Events repeat every cycle when looping; the unrolled range [from, to) may span several cycles.
    private fun fire(state: StateDef, from: Float, to: Float) {
        for (e in state.events) {
            var k = ceil(from - e.time).toInt()
            while (true) {
                val at = k + e.time
                if (at >= to) break
                if (at >= from && (state.loop || at <= 1f)) firedEvents.add(FiredEvent(state.name, e.name))
                if (!state.loop) break
                k++
            }
        }
    }

    private fun findTransition(): TransitionDef? {
        val source = nextState ?: currentState
        val t = maxOf(sourceProgress, if (nextState != null) nextTime else normalizedTime)
        for (tr in asset.transitions) {
            if (tr.from != null && tr.from != source) continue
            if (tr.from == null && source in tr.exceptFrom) continue
            if (tr.to == source) continue
            if (tr.conditions.isEmpty() && !tr.hasExitTime) continue
            if (tr.hasExitTime && t < tr.exitTime) continue
            if (!tr.conditions.all { it.test(parameters) }) continue
            return tr
        }
        return null
    }

    private fun startTransition(tr: TransitionDef) {
        tr.conditions.filter { it.op == ConditionOp.TRIGGER }.forEach { parameters.consumeTrigger(it.parameter) }
        if (nextState != null) {
            // Interrupted mid-fade: what is on screen becomes the fade source, so there is no pop.
            frozen.set(pose)
            fadeFromFrozen = true
        } else {
            fadeFromFrozen = false
        }
        nextState = tr.to
        nextTime = 0f
        fadeElapsed = 0f
        fadeDuration = blendOverrides[tr.to] ?: tr.duration
        if (fadeDuration <= 0f) completeTransition()
    }

    private fun completeTransition() {
        currentState = nextState ?: return
        normalizedTime = nextTime
        nextState = null
        fadeFromFrozen = false
    }

    private fun evaluate() {
        val ns = nextState
        if (ns == null) {
            motions.getValue(states.getValue(currentState).motion).evaluate(parameters, normalizedTime, pose)
            return
        }
        if (fadeFromFrozen) fromPose.set(frozen)
        else motions.getValue(states.getValue(currentState).motion).evaluate(parameters, normalizedTime, fromPose)
        motions.getValue(states.getValue(ns).motion).evaluate(parameters, nextTime, toPose)
        Pose.blend(fromPose, toPose, fadeElapsed / fadeDuration, pose)
    }
}
