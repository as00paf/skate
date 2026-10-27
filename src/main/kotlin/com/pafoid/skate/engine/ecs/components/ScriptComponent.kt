package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

/** Attaches a source script (e.g. `.kts`) to an entity. Runtime state lives in `ScriptRuntime`. */
@Serializable
class ScriptComponent(
    var path: String = "",
    val properties: MutableMap<String, String> = mutableMapOf(),
) : Component()
