package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
class ModularTile(
    var tileSize: Int = 1
) : Component() {
    @Contextual
    var size = Vector3f(tileSize * 1f, 1f, tileSize * 1f)
}