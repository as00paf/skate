package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.input.InputMappings

sealed class EngineAction(eventName: String) : Event(eventName) {
    data class SetRuntimePlaying(val playing: Boolean) : EngineAction("engine.set_runtime_playing")
    data class ApplyMappings(val mappings: InputMappings) : EngineAction("engine.apply_mappings") {}
}