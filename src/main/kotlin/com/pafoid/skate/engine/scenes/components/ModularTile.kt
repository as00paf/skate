package com.pafoid.skate.engine.scenes.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
class ModularTile : Component() {
    @Contextual var size = Vector3f(1f, 1f, 1f)
}