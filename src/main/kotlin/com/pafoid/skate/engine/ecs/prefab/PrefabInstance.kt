package com.pafoid.skate.engine.ecs.prefab

import com.pafoid.skate.engine.ecs.components.Component
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Marks the root of a prefab instance. [overrides] holds only the properties changed on the instance;
 * every other property follows the prefab. Keys are built by [PrefabOverrideKey].
 */
@Serializable
class PrefabInstance(
    var prefabId: String = "",
    val overrides: MutableMap<String, JsonElement> = mutableMapOf(),
) : Component()
