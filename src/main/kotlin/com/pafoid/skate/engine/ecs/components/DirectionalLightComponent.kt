package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Vector3f

@Serializable
data class DirectionalLightComponent(
    @Contextual
    val direction: Vector3f = Vector3f(0f, -1f, 0f),
    @Contextual
    val color: Vector3f = Vector3f(1f, 0.95f, 0.8f),
    var intensity: Float = 1f,
    @Contextual
    val lightSpaceMatrix: Matrix4f = Matrix4f(),
    var orthoLeft: Float = -20f,
    var orthoRight: Float = 20f,
    var orthoBottom: Float = -20f,
    var orthoTop: Float = 20f,
    var orthoNear: Float = 0.1f,
    var orthoFar: Float = 100f,
    var shadowDistance: Float = 50f,
    var autoCalculateBounds: Boolean = true,
    var stabilizeProjection: Boolean = true,
    var depthBias: Float = 0.001f,
    var slopeScaledBias: Float = 0.002f,
    var castShadows: Boolean = true
) : Component() {

    override fun reset() {
        direction.set(0f, -1f, 0f)
        color.set(1f, 0.95f, 0.8f)
        intensity = 1f
        lightSpaceMatrix.identity()
        orthoLeft = -20f
        orthoRight = 20f
        orthoBottom = -20f
        orthoTop = 20f
        orthoNear = 0.1f
        orthoFar = 100f
        shadowDistance = 50f
        autoCalculateBounds = true
        stabilizeProjection = true
        depthBias = 0.001f
        slopeScaledBias = 0.002f
        castShadows = true
    }
}