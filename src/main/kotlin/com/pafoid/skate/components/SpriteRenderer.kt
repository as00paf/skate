package com.pafoid.skate.components

import com.pafoid.skate.editor.MImGui
import com.pafoid.skate.core.Transform
import com.pafoid.skate.core.renderer.Texture
import org.joml.Vector2f
import org.joml.Vector4f

class SpriteRenderer(
    private var sprite: Sprite = Sprite(null),
    private var color: Vector4f = Vector4f(1f, 1f, 1f, 1f)
) : Component() {

    @Transient private var lastTransform: Transform = Transform()
    @Transient private var isDirty = true

    override fun start() {
        sprite.getTexture()?.let {
            sprite.setTexture(getTexture(it.getFilePath().orEmpty()))
        }

        this.lastTransform = gameObject.transform.copy()
    }

    override fun update(dt: Float) {
        if (lastTransform != gameObject.transform) {
            gameObject.transform.copy(lastTransform)
            isDirty = true
        }
    }

    override fun editorUpdate(dt: Float) {
        if (lastTransform != gameObject.transform) {
            gameObject.transform.copy(lastTransform)
            isDirty = true
        }

        if(lastTransform.rotation != gameObject.transform.rotation) {
            gameObject.transform.copy(lastTransform)
            isDirty = true
        }
    }

    override fun imgui() {
        if(MImGui.colorPicker4("Color Picker", this.color)){
            isDirty = true
        }
    }



    fun getColor() = color
    fun getTextCoords(): Array<Vector2f> = sprite.getTexCoords()
    fun isDirty() = isDirty
    fun setClean() { isDirty = false }
    fun setDirty() { isDirty = true }
    fun getTexture() = sprite.getTexture()
    fun setTexture(tex: Texture) {
        sprite.setTexture(tex)
    }

    fun setSprite(sprite: Sprite) {
        this.sprite = sprite
        isDirty = true
    }

    fun setColor(color: Vector4f) {
        if(this.color != color) {
            this.color.set(color)
            isDirty = true
        }
    }

    fun setColor(x:Float, y:Float, z: Float, w: Float) {
        val color = Vector4f(x, y, z, w)
        if(this.color != color) {
            this.color.set(color)
            isDirty = true
        }
    }
}