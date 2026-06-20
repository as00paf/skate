package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.render.data.CameraPreset
import com.pafoid.skate.engine.utils.Interpolator
import com.pafoid.skate.engine.utils.Ray
import com.pafoid.skate.engine.utils.toDegrees
import com.pafoid.skate.engine.utils.toRadians
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.asin
import kotlin.math.atan2

@Serializable
class Camera(
    @Contextual val position: Vector3f = Vector3f(),
    var pitch: Float = 0f,
    var yaw: Float = 0f,
    var roll: Float = 0f,
    var isOrthographic: Boolean = false
) {
    var fov = 45f
    var nearPlane = 0.1f
    var farPlane = 1000f

    @Contextual
    var projectionSize = Vector2f(32f, 18f) // Default 16:9 units
    var zoom = 1.0f

    // Viewport dimensions for aspect ratio calculation
    var viewportWidth: Int = 1920
    var viewportHeight: Int = 1080

    // Interpolation
    private var targetPreset: CameraPreset? = null
    private var lerpTime = 0f
    private var lerpDuration = 0f
    private var startFov = 0f
    private var startDistance = 0f

    // Cached matrices to reduce GC pressure
    @Transient
    private val projectionMatrix = Matrix4f()
    @Transient
    private val viewMatrix = Matrix4f()

    @Transient
    private val camForward = Vector3f(0f, 0f, -1f)
    @Transient
    private val camRight = Vector3f(1f, 0f, 0f)

    fun addZoom(value: Float) {
        zoom += value
        if (zoom <= 0.1f) {
            zoom = 0.1f
        }
    }

    fun applyPreset(preset: CameraPreset) {
        targetPreset = null
        fov = preset.fov
        zoom = 1.0f
    }

    fun lerpToPreset(preset: CameraPreset, duration: Float) {
        targetPreset = preset
        lerpDuration = duration
        lerpTime = 0f
        startFov = fov
        startDistance = zoom
    }

    fun update(dt: Float) {
        handleLerp(dt)
    }

    private fun handleLerp(dt: Float) {
        val target = targetPreset ?: return
        lerpTime += dt
        val t = (lerpTime / lerpDuration).coerceIn(0f, 1f)

        fov = Interpolator.lerp(startFov, target.fov, t)
        zoom = Interpolator.lerp(startDistance, target.zoom, t)

        if (t >= 1f) {
            targetPreset = null
        }
    }

    fun createProjectionMatrix(): Matrix4f {
        projectionMatrix.identity()

        // Calculate aspect ratio from current viewport dimensions
        val aspectRatio = if (viewportHeight > 0) {
            viewportWidth.toFloat() / viewportHeight.toFloat()
        } else {
            16f / 9f // Fallback to 16:9 if height is 0
        }

        if (isOrthographic) {
            val left = -projectionSize.x * zoom / 2f
            val right = projectionSize.x * zoom / 2f
            val bottom = -projectionSize.y * zoom / 2f
            val top = projectionSize.y * zoom / 2f
            projectionMatrix.ortho(left, right, bottom, top, nearPlane, farPlane)
        } else {
            projectionMatrix.perspective(Math.toRadians(fov.toDouble()).toFloat() * zoom, aspectRatio, nearPlane, farPlane)
        }

        return projectionMatrix
    }

    fun createViewMatrix(): Matrix4f {
        viewMatrix.identity()

        viewMatrix.rotate(pitch.toRadians(), Vector3f(1f, 0f, 0f))
        viewMatrix.rotate(yaw.toRadians(), Vector3f(0f, 1f, 0f))
        viewMatrix.rotate(roll.toRadians(), Vector3f(0f, 0f, 1f))

        val negativeCameraPos = Vector3f(position).negate()
        viewMatrix.translate(negativeCameraPos)

        return viewMatrix
    }

    fun getInverseView(): Matrix4f {
        return createViewMatrix().invert()
    }

    fun getInverseProjection(): Matrix4f {
        return createProjectionMatrix().invert()
    }

    fun lookAt(target: Vector3f) {
        val dir = Vector3f(target).sub(position).normalize()
        pitch = asin(-dir.y.toDouble()).toDegrees().toFloat()
        yaw = atan2(dir.x.toDouble(), -dir.z.toDouble()).toDegrees().toFloat()
    }

    fun screenToRay(screenX: Float, screenY: Float, width: Float, height: Float): Ray {
        // Convert screen coordinates to NDC (-1 to 1)
        val x = (2.0f * screenX) / width - 1.0f
        val y = 1.0f - (2.0f * screenY) / height
        
        val projectionMatrix = createProjectionMatrix()
        val viewMatrix = createViewMatrix()
        
        val invProjView = Matrix4f(projectionMatrix).mul(viewMatrix).invert()
        
        // Ray start (near plane) and end (far plane) in world space
        val near = Vector4f(x, y, -1f, 1f).mul(invProjView)
        val far = Vector4f(x, y, 1f, 1f).mul(invProjView)
        
        near.div(near.w)
        far.div(far.w)
        
        val rayOrigin = Vector3f(near.x, near.y, near.z)
        val rayDirection = Vector3f(far.x - near.x, far.y - near.y, far.z - near.z).normalize()
        
        return Ray(rayOrigin, rayDirection)
    }

    fun getForwardAndRight(): Pair<Vector3f, Vector3f> {
        camForward.set(Vector3f(0f, 0f, -1f))
        val viewInv = getInverseView()
        viewInv.transformDirection(camForward)
        camForward.y = 0f
        camForward.normalize()

        camRight.set(Vector3f(1f, 0f, 0f))
        viewInv.transformDirection(camRight)
        camRight.y = 0f
        camRight.normalize()

        return camForward to camRight
    }
}
