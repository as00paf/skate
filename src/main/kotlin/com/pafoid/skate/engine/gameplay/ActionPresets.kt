package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.input.ActionMap

/**
 * Action sets that game code contributes to the engine's [ActionMap] without the engine importing the game (INP-10).
 * A game registers its preset at startup (e.g. alongside its character behaviors); the engine applies every preset
 * when it starts and again when a scene loads, so both the editor and the exported game get them. A preset must only
 * define actions that are missing, so applying it again changes nothing and keeps the player's bindings.
 */
object ActionPresets {
    private val presets = LinkedHashMap<String, (ActionMap) -> Unit>()

    /** Adds or replaces the preset [id] (registering twice is harmless). */
    fun register(id: String, preset: (ActionMap) -> Unit) {
        presets[id] = preset
    }

    val ids: Set<String> get() = presets.keys

    fun applyTo(map: ActionMap): ActionMap {
        presets.values.forEach { it(map) }
        return map
    }
}
