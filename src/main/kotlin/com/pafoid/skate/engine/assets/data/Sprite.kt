package com.pafoid.skate.engine.assets.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

@Serializable
class Sprite(
    var texture: Texture? = null,
    var texCoords: Array<@Contextual Vector2f> = arrayOf(
    Vector2f(1f, 1f),
    Vector2f(1f, 0f),
    Vector2f(0f, 0f),
    Vector2f(0f, 1f)
    )
) {

    var width: Float = 0f
    var height: Float = 0f

    fun getTexId() = texture?.texId ?: 0

}