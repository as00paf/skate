package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.animation.procedural.ProceduralClipDef
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

/**
 * [from] == null means "any state" (except the states in [exceptFrom]). When [hasExitTime], the source must have
 * reached [exitTime] (normalized).
 */
@Serializable
data class TransitionDef(
    val from: String?,
    val to: String,
    val conditions: List<TransitionCondition> = emptyList(),
    val duration: Float = 0.2f,
    val hasExitTime: Boolean = false,
    val exitTime: Float = 1f,
    val exceptFrom: List<String> = emptyList(),
)

/** Value type of a controller parameter (ANI-21). Bools store their default as 0/1; triggers have none. */
enum class ParameterType { FLOAT, BOOL, TRIGGER }

/** A declared controller parameter (ANI-21 §4.1): gameplay and scripts write it, conditions and blend trees read it. */
@Serializable
data class ParameterDef(val name: String, val type: ParameterType, val default: Float = 0f)

/** Position of a state box in the Animator window's graph, in canvas units (ANI-21 §4.2). The runtime ignores it. */
@Serializable
data class NodePos(val x: Float, val y: Float)

/** Event fired when playback crosses [time] (normalized, 0..1) of a state's motion. */
@Serializable
data class AnimEventDef(val time: Float, val name: String)

@Serializable
data class StateDef(
    val name: String,
    /**
     * Key of the motion (clip or blend tree) in the runtime motion map. Empty: no clip yet, the state plays the rest
     * pose ([RestMotion], ANI-21 §4.4).
     */
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
    /**
     * Procedural stand-in clips, usable wherever a clip name is. A real clip with the same name on the animator
     * replaces the procedural one, so each entry is a named slot for a clip that can be dropped in later.
     */
    val poses: Map<String, ProceduralClipDef> = emptyMap(),
    /** Placement fixes for clips, keyed by clip name (see [ClipFitDef]). */
    val clipFits: Map<String, ClipFitDef> = emptyMap(),
    /** Format version (ANI-21 §4.6). Files written before ANI-21 have none and read as 1. */
    val version: Int = FORMAT_VERSION,
    /**
     * Declared parameters with their type and default (ANI-21 §4.1). Optional: undeclared names used by conditions or
     * blend trees still work (0 / off), see [ControllerParameters.effective].
     */
    val parameters: List<ParameterDef> = emptyList(),
    /** Graph box positions by state name, [ANY_STATE_NODE] for the Any State box (ANI-21 §4.2). Editor only. */
    val layout: Map<String, NodePos> = emptyMap(),
    /**
     * Clip files this controller brings (ANI-21 §4.3), as asset paths (project-relative like every AST-09 path). The
     * Animator loads them by name; a clip with the same name already on the Animator wins.
     */
    val clips: List<String> = emptyList(),
) {
    init {
        require(states.any { it.name == defaultState }) { "Unknown default state '$defaultState'" }
        val names = states.map { it.name }.toSet()
        require(names.size == states.size) { "Duplicate state names" }
        require(states.none { it.name.isBlank() }) { "A state needs a name" }
        require(parameters.map { it.name }.toSet().size == parameters.size) { "Duplicate parameter names" }
        transitions.forEach {
            require(it.to in names && (it.from == null || it.from in names) && it.exceptFrom.all { e -> e in names }) {
                "Transition references unknown state: $it"
            }
        }
    }

    /** Problems that would stop [Animator.useController] from building this controller; [availableClips] are the clip names on hand. */
    fun issues(availableClips: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        val clips = availableClips + poses.keys
        (clipFits.keys - availableClips).forEach { problems += "Clip fit '$it' names an unknown clip" }
        for ((key, def) in motions) {
            (def.clipNames() - clips).forEach { problems += "Motion '$key' uses unknown clip '$it'" }
        }
        for (state in states) {
            if (state.motion.isNotEmpty() && state.motion !in motions && state.motion !in clips) problems += "State '${state.name}' uses unknown motion '${state.motion}'"
            state.events.filter { it.time !in 0f..1f }.forEach { problems += "State '${state.name}' event '${it.name}' time ${it.time} is outside 0..1" }
        }
        return problems
    }

    /**
     * Problems that do not stop the controller from running (ANI-21 §4.1, §4.4): states without a clip (they play the
     * rest pose), conditions on undeclared parameters (only when some are declared) and operators that do not fit the
     * declared type. Shown in the Animator window.
     */
    fun warnings(): List<String> {
        val out = mutableListOf<String>()
        states.filter { it.motion.isEmpty() }.forEach { out += "State '${it.name}' has no clip: it plays the rest pose" }
        val declared = parameters.associateBy { it.name }
        for (t in transitions) for (c in t.conditions) {
            val p = declared[c.parameter]
            if (p == null) {
                if (parameters.isNotEmpty()) out += "Transition ${t.from ?: "Any State"} -> ${t.to} uses undeclared parameter '${c.parameter}'"
            } else if (c.op !in ControllerParameters.operatorsFor(p.type)) {
                out += "Transition ${t.from ?: "Any State"} -> ${t.to}: '${c.parameter}' is a ${p.type.name.lowercase()}, '${c.op.name.lowercase()}' does not apply"
            }
        }
        return out
    }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        const val FORMAT_VERSION = 1
        /** [layout] key of the Any State box. */
        const val ANY_STATE_NODE = "@any"

        private val JSON = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
        fun fromJson(text: String): AnimationControllerAsset = JSON.decodeFromString(serializer(), text)
    }
}
