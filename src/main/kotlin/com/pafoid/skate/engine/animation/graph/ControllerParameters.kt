package com.pafoid.skate.engine.animation.graph

/** Parameter helpers for controllers (ANI-21 §4.1): types, operators and the list inferred from old files. */
object ControllerParameters {

    /** Condition operators that make sense for a parameter of [type]. */
    fun operatorsFor(type: ParameterType): List<ConditionOp> = when (type) {
        ParameterType.FLOAT -> listOf(ConditionOp.GREATER, ConditionOp.LESS, ConditionOp.EQUALS)
        ParameterType.BOOL -> listOf(ConditionOp.TRUE, ConditionOp.FALSE)
        ParameterType.TRIGGER -> listOf(ConditionOp.TRIGGER)
    }

    /** The type an operator implies for an undeclared parameter. */
    fun typeOf(op: ConditionOp): ParameterType = when (op) {
        ConditionOp.TRUE, ConditionOp.FALSE -> ParameterType.BOOL
        ConditionOp.TRIGGER -> ParameterType.TRIGGER
        else -> ParameterType.FLOAT
    }

    /** Every parameter name a condition or blend tree of [asset] reads, in first-use order. */
    fun usedNames(asset: AnimationControllerAsset): List<String> {
        val names = LinkedHashSet<String>()
        asset.transitions.forEach { t -> t.conditions.forEach { names += it.parameter } }
        asset.motions.values.forEach { names += blendParameters(it) }
        return names.toList()
    }

    /**
     * The declared parameters followed by the undeclared ones, inferred from use: a bool for `TRUE`/`FALSE`, a
     * trigger for `TRIGGER`, a float otherwise (and for blend-tree axes).
     */
    fun effective(asset: AnimationControllerAsset): List<ParameterDef> {
        val declared = asset.parameters.map { it.name }.toSet()
        val inferred = LinkedHashMap<String, ParameterType>()
        for (t in asset.transitions) for (c in t.conditions) {
            if (c.parameter !in declared) inferred.putIfAbsent(c.parameter, typeOf(c.op))
        }
        asset.motions.values.forEach { m -> blendParameters(m).filter { it !in declared }.forEach { inferred.putIfAbsent(it, ParameterType.FLOAT) } }
        return asset.parameters + inferred.map { (name, type) -> ParameterDef(name, type) }
    }

    /** Where [name] is read: "Transition a -> b" and "Motion key" entries (empty when unused). */
    fun usages(asset: AnimationControllerAsset, name: String): List<String> {
        val out = mutableListOf<String>()
        asset.transitions.filter { t -> t.conditions.any { it.parameter == name } }
            .forEach { out += "${it.from ?: "Any State"} -> ${it.to}" }
        asset.motions.filter { (_, m) -> name in blendParameters(m) }.forEach { out += it.key }
        return out
    }

    /** Seeds [into] with the declared defaults (floats and bools; triggers start unset). */
    fun seed(asset: AnimationControllerAsset, into: AnimParameters) {
        for (p in asset.parameters) when (p.type) {
            ParameterType.FLOAT -> into.setFloat(p.name, p.default)
            ParameterType.BOOL -> into.setBool(p.name, p.default != 0f)
            ParameterType.TRIGGER -> Unit
        }
    }

    internal fun blendParameters(def: MotionDef): Set<String> = when (def) {
        is ClipRef -> emptySet()
        is Blend1DDef -> setOf(def.parameter) + def.children.flatMap { blendParameters(it.motion) }
        is Blend2DDef -> setOf(def.parameterX, def.parameterY) + def.children.flatMap { blendParameters(it.motion) }
    }

    /** [def] with every reference to parameter [old] renamed to [new]. */
    internal fun renameIn(def: MotionDef, old: String, new: String): MotionDef = when (def) {
        is ClipRef -> def
        is Blend1DDef -> def.copy(
            parameter = if (def.parameter == old) new else def.parameter,
            children = def.children.map { it.copy(motion = renameIn(it.motion, old, new)) },
        )
        is Blend2DDef -> def.copy(
            parameterX = if (def.parameterX == old) new else def.parameterX,
            parameterY = if (def.parameterY == old) new else def.parameterY,
            children = def.children.map { it.copy(motion = renameIn(it.motion, old, new)) },
        )
    }
}
