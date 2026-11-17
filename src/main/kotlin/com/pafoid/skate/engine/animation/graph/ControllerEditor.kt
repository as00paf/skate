package com.pafoid.skate.engine.animation.graph

/**
 * Editing facade over an immutable [AnimationControllerAsset], meant to sit behind editor commands.
 * Every operation validates by rebuilding the asset: on failure it throws [IllegalArgumentException] and
 * [asset] is left unchanged. Take `editor.asset` before/after an edit for undo/redo snapshots.
 */
class ControllerEditor(asset: AnimationControllerAsset) {
    var asset: AnimationControllerAsset = asset
        private set

    private inline fun edit(change: AnimationControllerAsset.() -> AnimationControllerAsset): AnimationControllerAsset {
        asset = asset.change()
        return asset
    }

    fun addState(state: StateDef) = edit { copy(states = states + state) }

    fun updateState(name: String, change: (StateDef) -> StateDef) = edit {
        require(states.any { it.name == name }) { "Unknown state '$name'" }
        copy(states = states.map { if (it.name == name) change(it) else it })
    }

    /** Removes the state and every transition touching it; the default moves to the first remaining state. */
    fun removeState(name: String) = edit {
        require(states.any { it.name == name }) { "Unknown state '$name'" }
        val remaining = states.filter { it.name != name }
        require(remaining.isNotEmpty()) { "A controller needs at least one state" }
        copy(
            states = remaining,
            defaultState = if (defaultState == name) remaining.first().name else defaultState,
            transitions = transitions.filter { it.from != name && it.to != name }.map { it.copy(exceptFrom = it.exceptFrom - name) },
            layout = layout - name,
        )
    }

    fun renameState(old: String, new: String) = edit {
        require(states.any { it.name == old }) { "Unknown state '$old'" }
        require(new.isNotBlank()) { "A state needs a name" }
        require(states.none { it.name == new }) { "State '$new' already exists" }
        copy(
            layout = layout[old]?.let { (layout - old) + (new to it) } ?: layout,
            states = states.map { if (it.name == old) it.copy(name = new) else it },
            defaultState = if (defaultState == old) new else defaultState,
            transitions = transitions.map {
                it.copy(
                    from = if (it.from == old) new else it.from,
                    to = if (it.to == old) new else it.to,
                    exceptFrom = it.exceptFrom.map { e -> if (e == old) new else e },
                )
            },
        )
    }

    fun setDefaultState(name: String) = edit { copy(defaultState = name) }

    fun setName(name: String) = edit {
        require(name.isNotBlank()) { "A controller needs a name" }
        copy(name = name)
    }

    /** Adds a state named [base] (or "[base] 2", ...) at [pos]; returns its name. */
    fun addState(base: String, motion: String, pos: NodePos?): String {
        val name = freeStateName(base)
        edit { copy(states = states + StateDef(name, motion), layout = if (pos != null) layout + (name to pos) else layout) }
        return name
    }

    /**
     * Copies [name] (motion, speed, loop, events) to a free name, [offset] away in the graph. With [withTransitions]
     * the copy also gets every transition into and out of the original, Any State ones included, retargeted to it
     * (ANI-21 Q3). Returns the copy's name.
     */
    fun duplicateState(name: String, withTransitions: Boolean, offset: NodePos = NodePos(30f, 30f)): String {
        val original = asset.states.firstOrNull { it.name == name } ?: throw IllegalArgumentException("Unknown state '$name'")
        val copyName = freeStateName(name)
        edit {
            val pos = layout[name]?.let { NodePos(it.x + offset.x, it.y + offset.y) }
            val extra = if (!withTransitions) emptyList() else transitions.flatMap { t ->
                when {
                    t.from == name && t.to == name -> listOf(t.copy(from = copyName, to = copyName))
                    t.from == name -> listOf(t.copy(from = copyName))
                    t.to == name -> listOf(t.copy(to = copyName))
                    else -> emptyList()
                }
            }
            copy(
                states = states + original.copy(name = copyName),
                transitions = transitions.map { if (withTransitions && name in it.exceptFrom) it.copy(exceptFrom = it.exceptFrom + copyName) else it } + extra,
                layout = if (pos != null) layout + (copyName to pos) else layout,
            )
        }
        return copyName
    }

    /** [base], or "[base] 2", "[base] 3", ... when a state already has that name. */
    fun freeStateName(base: String): String {
        val clean = base.trim().ifEmpty { "New State" }
        val taken = asset.states.map { it.name }.toSet()
        if (clean !in taken) return clean
        val stem = clean.replace(Regex(""" \d+$"""), "")
        var n = 2
        while ("$stem $n" in taken) n++
        return "$stem $n"
    }

    /** Sets graph positions (merged into the layout). */
    fun setPositions(positions: Map<String, NodePos>) = edit { copy(layout = layout + positions) }

    /**
     * Pastes [states] (copied from this or another controller) with the [transitions] between them and their
     * [positions] moved by [offset]. Names that are taken get a free name; returns the old -> new names.
     */
    fun pasteStates(states: List<StateDef>, transitions: List<TransitionDef>, positions: Map<String, NodePos>, offset: NodePos): Map<String, String> {
        val renames = LinkedHashMap<String, String>()
        for (s in states) {
            val name = freeStateName(s.name)
            renames[s.name] = name
            edit {
                copy(
                    states = this.states + s.copy(name = name),
                    layout = positions[s.name]?.let { layout + (name to NodePos(it.x + offset.x, it.y + offset.y)) } ?: layout,
                )
            }
        }
        val copied = transitions.filter { it.from != null && it.from in renames && it.to in renames }
            .map { it.copy(from = renames[it.from], to = renames.getValue(it.to), exceptFrom = emptyList()) }
        if (copied.isNotEmpty()) edit { copy(transitions = this.transitions + copied) }
        return renames
    }

    // ---- parameters (ANI-21 §4.1) --------------------------------------------------------------------------------

    /** Declares a parameter. Declaring an inferred (used but undeclared) name keeps its uses. */
    fun addParameter(def: ParameterDef) = edit {
        require(def.name.isNotBlank()) { "A parameter needs a name" }
        require(parameters.none { it.name == def.name }) { "Parameter '${def.name}' already exists" }
        copy(parameters = parameters + def)
    }

    /** Changes type or default of a declared parameter (declaring it first when it was only inferred). */
    fun updateParameter(name: String, change: (ParameterDef) -> ParameterDef) = edit {
        val current = parameters.firstOrNull { it.name == name }
            ?: ControllerParameters.effective(this).firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("Unknown parameter '$name'")
        val next = change(current)
        require(next.name == name) { "Use renameParameter to rename" }
        copy(parameters = if (parameters.any { it.name == name }) parameters.map { if (it.name == name) next else it } else parameters + next)
    }

    /** Renames a parameter everywhere: its declaration, every condition and every blend tree axis. */
    fun renameParameter(old: String, new: String) = edit {
        require(new.isNotBlank()) { "A parameter needs a name" }
        val known = ControllerParameters.effective(this).map { it.name }
        require(old in known) { "Unknown parameter '$old'" }
        require(new == old || new !in known) { "Parameter '$new' already exists" }
        copy(
            parameters = parameters.map { if (it.name == old) it.copy(name = new) else it },
            transitions = transitions.map { t -> t.copy(conditions = t.conditions.map { if (it.parameter == old) it.copy(parameter = new) else it }) },
            motions = motions.mapValues { (_, m) -> ControllerParameters.renameIn(m, old, new) },
        )
    }

    /** Removes a declared parameter; refused while a condition or blend tree reads it. */
    fun removeParameter(name: String) = edit {
        val used = ControllerParameters.usages(this, name)
        require(used.isEmpty()) { "Parameter '$name' is used by: ${used.joinToString()}" }
        require(parameters.any { it.name == name }) { "Unknown parameter '$name'" }
        copy(parameters = parameters.filter { it.name != name })
    }

    /**
     * The skater convention (ANI-21 §3.4): a transition [from] -> [to] that fires when the bool parameter named
     * after [to] is on (`Animator.play("to")`), declaring that bool when missing. Returns the transition's index.
     */
    fun addRequestTransition(from: String?, to: String): Int {
        edit {
            val declare = parameters.none { it.name == to } && ControllerParameters.effective(this).none { it.name == to }
            copy(
                parameters = if (declare) parameters + ParameterDef(to, ParameterType.BOOL) else parameters,
                transitions = transitions + TransitionDef(from, to, listOf(TransitionCondition(to, ConditionOp.TRUE))),
            )
        }
        return asset.transitions.lastIndex
    }

    // ---- clip files (ANI-21 §4.3) --------------------------------------------------------------------------------

    fun addClipFile(path: String) = edit { if (path in clips) this else copy(clips = clips + path) }

    fun removeClipFile(path: String) = edit { copy(clips = clips - path) }

    fun addTransition(transition: TransitionDef) = edit { copy(transitions = transitions + transition) }

    fun updateTransition(index: Int, change: (TransitionDef) -> TransitionDef) = edit {
        require(index in transitions.indices) { "No transition at $index" }
        copy(transitions = transitions.mapIndexed { i, t -> if (i == index) change(t) else t })
    }

    fun removeTransition(index: Int) = edit {
        require(index in transitions.indices) { "No transition at $index" }
        copy(transitions = transitions.filterIndexed { i, _ -> i != index })
    }

    /** Moves a transition; evaluation order is list order, so this sets priority. */
    fun moveTransition(from: Int, to: Int) = edit {
        require(from in transitions.indices && to in transitions.indices) { "Transition index out of range" }
        copy(transitions = transitions.toMutableList().also { it.add(to, it.removeAt(from)) })
    }

    fun addEvent(state: String, event: AnimEventDef) = updateState(state) { it.copy(events = (it.events + event).sortedBy { e -> e.time }) }

    fun removeEvent(state: String, index: Int) = updateState(state) {
        require(index in it.events.indices) { "No event at $index" }
        it.copy(events = it.events.filterIndexed { i, _ -> i != index })
    }

    /** Defines or replaces the named motion (clip ref or blend tree) that states can reference by key. */
    fun setMotion(key: String, def: MotionDef) = edit { copy(motions = motions + (key to def)) }

    /** Removes a named motion; refuses while a state still points at it. */
    fun removeMotion(key: String) = edit {
        require(states.none { it.motion == key }) { "Motion '$key' is used by state '${states.first { s -> s.motion == key }.name}'" }
        copy(motions = motions - key)
    }

    fun <T : MotionDef> updateMotion(key: String, type: Class<T>, change: (T) -> MotionDef) = edit {
        val current = motions[key] ?: throw IllegalArgumentException("Unknown motion '$key'")
        require(type.isInstance(current)) { "Motion '$key' is a ${current::class.simpleName}" }
        @Suppress("UNCHECKED_CAST")
        copy(motions = motions + (key to change(current as T)))
    }

    fun updateBlend1D(key: String, change: (Blend1DDef) -> Blend1DDef) = updateMotion(key, Blend1DDef::class.java, change)
    fun updateBlend2D(key: String, change: (Blend2DDef) -> Blend2DDef) = updateMotion(key, Blend2DDef::class.java, change)
}
