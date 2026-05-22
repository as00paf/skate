package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector2f
import org.joml.Vector4f

@Serializable
class SpriteRenderer(
    @Contextual private val color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    @Transient private var sprite: Sprite = Sprite(),
    val zIndex: Int = 0
): Component() {

    @Transient
    private var lastTransform: Transform = Transform()
    @Transient
    private var isDirty = true

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        gameObject.getComponent<Transform>()?.let { this.lastTransform.copyFrom(it) }
    }

    override fun update(dt: Float) {
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