package com.pafoid.skate.engine.assets.data

import org.joml.Vector2f

class SpriteSheet(
    private var texture: Texture,
    private var spriteWidth: Int,
    private var spriteHeight: Int,
    private var spriteCount: Int,
    private var spacing: Int)
{

    private val sprites = mutableListOf<Sprite>()

    init {
        var currentX = 0
        var currentY = texture.height - spriteHeight
        for(i in 0 until spriteCount) {
            val topY = (currentY + spriteHeight) / texture.height.toFloat()
            val rightX = (currentX + spriteWidth) / texture.width.toFloat()
            val leftX = currentX / texture.width.toFloat()
            val bottomY = currentY / texture.height.toFloat()

            val textCoords = arrayOf(
                Vector2f(rightX, topY),
                Vector2f(rightX, bottomY),
                Vector2f(leftX, bottomY),
                Vector2f(leftX, topY)
            )

            val sprite = Sprite(texture, textCoords)
            sprite.width = spriteWidth.toFloat()
            sprite.height = spriteHeight.toFloat()

            sprites.add(sprite)

            currentX += spriteWidth + spacing
            if(currentX >= texture.width) {
                currentX = 0
                currentY -= spriteHeight + spacing
            }
        }
    }

    fun getSprite(index: Int) = sprites[index]

    fun size():Int = sprites.size
}