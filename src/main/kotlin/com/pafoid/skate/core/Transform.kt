package com.pafoid.skate.core

import com.pafoid.skate.components.Component
import com.pafoid.skate.editor.MImGui
import org.joml.Vector2f

class Transform(
    var position:Vector2f = Vector2f(),
    var scale: Vector2f = Vector2f(),
    var rotation: Double = 0.0,
    var zIndex:Int = 0
): Component() {

    override fun imgui() {
        gameObject.name = MImGui.inputText("Name: ", gameObject.name)
        MImGui.drawVec2Control("Position", position)
        MImGui.drawVec2Control("Scale", scale, 32f)
        this.rotation = MImGui.dragFloat("Rotation", rotation.toFloat()).toDouble()
        this.zIndex = MImGui.dragInt("zIndex", zIndex)
    }

    fun copy(): Transform {
        return Transform(Vector2f(this.position), Vector2f(this.scale)/*, this.rotation, this.zIndex*/)
    }

    fun copy(to: Transform) {
        to.position.set(this.position)
        to.scale.set(scale)
    }

    override fun equals(other: Any?): Boolean {
        val transform = other as? Transform ?: return false
        val result =
            transform.position == position &&
            transform.scale == scale &&
            transform.rotation == rotation &&
            transform.zIndex == zIndex

        return result
    }
}