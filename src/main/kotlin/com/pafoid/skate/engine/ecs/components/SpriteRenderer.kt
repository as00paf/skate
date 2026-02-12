package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.assets.data.Texture
import org.joml.Vector2f
import org.joml.Vector4f

class SpriteRenderer(
    private val color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    private var sprite: Sprite = Sprite()
): Component() {

    private var lastTransform: Transform = Transform()
    private var isDirty = true

    override fun start() {
        gameObject.getComponent<Transform>()?.let { this.lastTransform.copyFrom(it) }
    }

    override fun update(dt: Float) {
        if (this.lastTransform != this.gameObject.getComponent<Transform>()) {
            this.gameObject.getComponent<Transform>()?.let { this.lastTransform.copyFrom(it) }
            isDirty = true
        }
    }

    override fun editorUpdate(dt: Float) {
        if (this.lastTransform != this.gameObject.getComponent<Transform>()) {
            this.gameObject.getComponent<Transform>()?.let { this.lastTransform.copyFrom(it) }
            isDirty = true
        }
    }

    fun getTexture(): Texture? {
        return sprite.getTexture()
    }

    fun getTexCoords(): Array<Vector2f> {
        return sprite.getTexCoords()
    }

    fun setSprite(sprite: Sprite) {
        this.sprite = sprite
        this.isDirty = true
    }

    fun setColor(color: Vector4f) {
        if (this.color != color) {
            this.color.set(color)
            this.isDirty = true
        }
    }
    
    fun getColor(): Vector4f {
        return this.color
    }

    fun isDirty(): Boolean {
        return isDirty
    }

    fun setClean() {
        this.isDirty = false
    }
    
    fun setTexture(texture: Texture) {
        this.sprite.setTexture(texture)
    }
}