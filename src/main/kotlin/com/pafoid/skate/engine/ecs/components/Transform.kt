package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.*

@Serializable
data class Transform(
    @Contextual val translation: Vector3f = Vector3f(),
    @Contextual val scale: Vector3f = Vector3f(1f, 1f, 1f),
    @Contextual val rotation: Vector3f = Vector3f()
): Component() {
    @Transient
    private val initialTranslation = Vector3f(translation)
    @Transient
    private val initialScale = Vector3f(scale)
    @Transient
    private val initialRotation = Vector3f(rotation)

    @Transient
    val localMatrix: Matrix4f = Matrix4f()

    @Transient
    val worldMatrix: Matrix4f = Matrix4f()

    override fun reset() {
        super.reset()
        translation.set(initialTranslation)
        scale.set(initialScale)
        rotation.set(initialRotation)
    }

    fun copyFrom(from: Transform) {
        this.translation.set(from.translation)
        this.scale.set(from.scale)
        this.rotation.set(from.rotation)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Transform) return false

        return other.translation == this.translation && other.scale == this.scale && other.rotation == this.rotation
    }

    override fun hashCode(): Int {
        return Objects.hash(translation, scale, rotation)
    }
}