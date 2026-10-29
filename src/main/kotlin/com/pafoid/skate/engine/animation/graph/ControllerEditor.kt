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
            transitions = transitions.filter { it.from != name && it.to != name },
        )
    }

    fun renameState(old: String, new: String) = edit {
        require(states.any { it.name == old }) { "Unknown state '$old'" }
        require(states.none { it.name == new }) { "State '$new' already exists" }
        copy(
            states = states.map { if (it.name == old) it.copy(name = new) else it },
            defaultState = if (defaultState == old) new else defaultState,
            transitions = transitions.map { it.copy(from = if (it.from == old) new else it.from, to = if (it.to == old) new else it.to) },
        )
    }

    fun setDefaultState(name: String) = edit { copy(defaultState = name) }

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
