package com.pafoid.skate.engine.assets.data

import org.joml.Vector2f

class Sprite(
    private var texture: Texture? = null,
    private var texCoords:Array<Vector2f> = arrayOf(
    Vector2f(1f, 1f),
    Vector2f(1f, 0f),
    Vector2f(0f, 0f),
    Vector2f(0f, 1f)
    )
) {

    var width: Float = 0f
    var height: Float = 0f

    fun getTexCoords() = texCoords
    fun getTexId() = texture?.texId ?: 0
    fun getTexture() = texture
    fun setTexture(tex: Texture) {
        texture = tex
    }

}