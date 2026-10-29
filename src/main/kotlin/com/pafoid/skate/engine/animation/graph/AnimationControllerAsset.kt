package com.pafoid.skate.engine.animation.graph

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ConditionOp { GREATER, LESS, EQUALS, TRUE, FALSE, TRIGGER }

@Serializable
data class TransitionCondition(
    val parameter: String,
    val op: ConditionOp,
    val threshold: Float = 0f,
) {
    fun test(p: AnimParameters): Boolean = when (op) {
        ConditionOp.GREATER -> p.getFloat(parameter) > threshold
        ConditionOp.LESS -> p.getFloat(parameter) < threshold
        ConditionOp.EQUALS -> kotlin.math.abs(p.getFloat(parameter) - threshold) < 1e-4f
        ConditionOp.TRUE -> p.getBool(parameter)
        ConditionOp.FALSE -> !p.getBool(parameter)
        ConditionOp.TRIGGER -> p.hasTrigger(parameter)
    }
}

/** [from] == null means "any state". When [hasExitTime], the source must have reached [exitTime] (normalized). */
@Serializable
data class TransitionDef(
    val from: String?,
    val to: String,
    val conditions: List<TransitionCondition> = emptyList(),
    val duration: Float = 0.2f,
    val hasExitTime: Boolean = false,
    val exitTime: Float = 1f,
)

/** Event fired when playback crosses [time] (normalized, 0..1) of a state's motion. */
@Serializable
data class AnimEventDef(val time: Float, val name: String)

@Serializable
data class StateDef(
    val name: String,
    /** Key of the motion (clip or blend tree) in the runtime motion map. */
    val motion: String,
    val speed: Float = 1f,
    val loop: Boolean = true,
    val events: List<AnimEventDef> = emptyList(),
)

/** Serializable state machine asset (JSON). */
@Serializable
data class AnimationControllerAsset(
    val name: String,
    val defaultState: String,
    val states: List<StateDef>,
    val transitions: List<TransitionDef> = emptyList(),
    /** Named blend trees/clip refs. A state's `motion` key resolves here first, then to a clip name. */
    val motions: Map<String, MotionDef> = emptyMap(),
) {
    init {
        require(states.any { it.name == defaultState }) { "Unknown default state '$defaultState'" }
        val names = states.map { it.name }.toSet()
        require(names.size == states.size) { "Duplicate state names" }
        transitions.forEach {
            require(it.to in names && (it.from == null || it.from in names)) { "Transition references unknown state: $it" }
        }
    }

    /** Problems that would stop [Animator.useController] from building this controller; [availableClips] are the clip names on hand. */
    fun issues(availableClips: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        for ((key, def) in motions) {
            (def.clipNames() - availableClips).forEach { problems += "Motion '$key' uses unknown clip '$it'" }
        }
        for (state in states) {
            if (state.motion !in motions && state.motion !in availableClips) problems += "State '${state.name}' uses unknown motion '${state.motion}'"
            state.events.filter { it.time !in 0f..1f }.forEach { problems += "State '${state.name}' event '${it.name}' time ${it.time} is outside 0..1" }
        }
        return problems
    }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
        fun fromJson(text: String): AnimationControllerAsset = JSON.decodeFromString(serializer(), text)
    }
}
