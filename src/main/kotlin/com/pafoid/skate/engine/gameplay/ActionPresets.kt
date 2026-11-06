package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.input.ActionMap

/**
 * Action sets that game code contributes to the engine's [ActionMap] without the engine importing the game (INP-10).
 * A game registers its preset at startup (e.g. alongside its character behaviors); the engine applies every preset
 * when it starts, when a scene loads and after [invalidate], so both the editor and the exported game get them. A
 * preset must only define what is missing or changed, so applying it again changes nothing and keeps the player's
 * bindings (profile overrides are kept by action id).
 */
object ActionPresets {
    private val presets = LinkedHashMap<String, (ActionMap) -> Unit>()

    /** Incremented when a preset registers or asks to be applied again; the engine re-applies on the next frame. */
    @Volatile
    var revision: Int = 0
        private set

    /** Adds or replaces the preset [id] (registering twice is harmless). */
    fun register(id: String, preset: (ActionMap) -> Unit) {
        presets[id] = preset
        revision++
    }

    /** A preset's actions changed (e.g. the player picked another control scheme): apply again on the next frame. */
    fun invalidate() {
        revision++
    }

    val ids: Set<String> get() = presets.keys

    fun applyTo(map: ActionMap): ActionMap {
        presets.values.forEach { it(map) }
        return map
    }
}
