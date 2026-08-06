package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.utils.toDegrees
import com.pafoid.skate.engine.utils.toRadians
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.asin
import kotlin.math.atan2

@Serializable
class CameraComponent : Component() {

    @Contextual
    val position: Vector3f = Vector3f()
    var pitch: Float = 0f
    var yaw: Float = 0f
    var roll: Float = 0f
    var isOrthographic: Boolean = false

    var fov = 45f
    var nearPlane = 0.1f
    var farPlane = 1000f

    @Contextual
    var projectionSize = Vector2f(32f, 18f) // Default 16:9 units
    var zoom = 1.0f

    // Viewport dimensions for aspect ratio calculation
    var viewportWidth: Int = 1920
        set(value) {
            field = value
            updateAspectRatio()
        }

    var viewportHeight: Int = 1080
        set(value) {
            field = value
            updateAspectRatio()
        }

    var aspectRatio: Float = 16f / 9f

    @Transient
    val camForward = Vector3f(0f, 0f, -1f)
    @Transient
    val camRight = Vector3f(1f, 0f, 0f)
    @Transient
    val projection = Matrix4f()
    @Transient
    val inverseProjection = Matrix4f()
    @Transient
    val view = Matrix4f()
    @Transient
    val inverseView = Matrix4f()

    override fun update(dt: Float) {
        calculateForwardAndRight()
        calculateProjection()
        calculateView()
    }

    private fun calculateProjection() {
        projection.identity()

        if (isOrthographic) {
            val left = -projectionSize.x * zoom / 2f
            val right = projectionSize.x * zoom / 2f
            val bottom = -projectionSize.y * zoom / 2f
            val top = projectionSize.y * zoom / 2f
            projection.ortho(left, right, bottom, top, nearPlane, farPlane)
        } else {
            projection.perspective(Math.toRadians(fov.toDouble()).toFloat() * zoom, aspectRatio, nearPlane, farPlane)
        }
        Matrix4f(projection).invert(inverseProjection)
    }

    private fun calculateView() {
        view.identity()

        view.rotate(pitch.toRadians(), Vector3f(1f, 0f, 0f))
        view.rotate(yaw.toRadians(), Vector3f(0f, 1f, 0f))
        view.rotate(roll.toRadians(), Vector3f(0f, 0f, 1f))

        val negativeCameraPos = Vector3f(position).negate()
        view.translate(negativeCameraPos)
        Matrix4f(view).invert(inverseView)
    }

    fun lookAt(target: Vector3f) {
        val dir = Vector3f(target).sub(position).normalize()
        pitch = asin(-dir.y.toDouble()).toDegrees().toFloat()
        yaw = atan2(dir.x.toDouble(), -dir.z.toDouble()).toDegrees().toFloat()
    }

    private fun calculateForwardAndRight(): Pair<Vector3f, Vector3f> {
        camForward.set(Vector3f(0f, 0f, -1f))
        val viewInv = Matrix4f(inverseView)
        viewInv.transformDirection(camForward)
        camForward.y = 0f
        camForward.normalize()

        camRight.set(Vector3f(1f, 0f, 0f))
        viewInv.transformDirection(camRight)
        camRight.y = 0f
        camRight.normalize()

        return camForward to camRight
    }

    private fun updateAspectRatio() {
        aspectRatio = if (viewportHeight > 0) {
            viewportWidth.toFloat() / viewportHeight.toFloat()
        } else {
            16f / 9f // Fallback to 16:9 if height is 0
        }
    }

}