package com.pafoid.skate.engine.assets

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
        var currentY = texture.getHeight() - spriteHeight
        for(i in 0 until spriteCount) {
            val topY = (currentY + spriteHeight) / texture.getHeight().toFloat()
            val rightX = (currentX + spriteWidth) / texture.getWidth().toFloat()
            val leftX = currentX / texture.getWidth().toFloat()
            val bottomY = currentY / texture.getHeight().toFloat()

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
            if(currentX >= texture.getWidth()) {
                currentX = 0
                currentY -= spriteHeight + spacing
            }
        }
    }

    fun getSprite(index: Int) = sprites[index]

    fun size():Int = sprites.size
}