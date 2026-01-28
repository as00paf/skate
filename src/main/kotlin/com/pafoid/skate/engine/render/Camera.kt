package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.controls.JoystickListener
import com.pafoid.skate.engine.controls.IInputProvider
import com.pafoid.skate.engine.controls.InputProvider
import com.pafoid.skate.engine.utils.toRadians
import com.pafoid.skate.engine.utils.toDegrees
import com.pafoid.skate.engine.utils.Interpolation
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW.*

data class CameraPreset(
    val fov: Float,
    val distance: Float,
    val offset: Vector3f
) {
    companion object {
        val LOW = CameraPreset(fov = 45f, distance = 3.0f, offset = Vector3f(0f, 0.4f, 0f))
        val HIGH = CameraPreset(fov = 50f, distance = 4.5f, offset = Vector3f(0f, 0.8f, 0f))
        val WIDE = CameraPreset(fov = 70f, distance = 5.0f, offset = Vector3f(0f, 0.6f, 0f))
    }
}

class Camera(
    val position: Vector3f = Vector3f(),
    var pitch: Float = 0f,
    var yaw: Float = 0f,
    var roll: Float = 0f,
    var isOrthographic: Boolean = false
) {
    var fov = 45f
    var nearPlane = 0.1f
    var farPlane = 1000f
    
    var _projectionSize = Vector2f(32f, 18f) // Default 16:9 units
    var zoom = 1.0f

    // Input
    var inputProvider: IInputProvider = InputProvider

    // Third person / Spring arm
    var target: Vector3f? = null
    var desiredDistance = 10.0f
    var targetOffset = Vector3f(0f, 0.5f, 0f)
    private var currentDistance = 10.0f

    // Interpolation
    private var targetPreset: CameraPreset? = null
    private var lerpTime = 0f
    private var lerpDuration = 0f
    private var startFov = 0f
    private var startDistance = 0f
    private val startOffset = Vector3f()

    fun addZoom(value: Float) {
        zoom += value
        if (zoom <= 0.1f) {
            zoom = 0.1f
        }
    }

    fun getProjectionSize(): Vector2f {
        return _projectionSize
    }

    fun applyPreset(preset: CameraPreset) {
        this.fov = preset.fov
        this.desiredDistance = preset.distance
        this.targetOffset.set(preset.offset)
        this.targetPreset = null
    }

    fun lerpToPreset(preset: CameraPreset, duration: Float) {
        this.targetPreset = preset
        this.lerpDuration = duration
        this.lerpTime = 0f
        this.startFov = fov
        this.startDistance = desiredDistance
        this.startOffset.set(targetOffset)
    }

    fun update(dt: Float) {
        handleLerp(dt)
        if (target != null) {
            updateThirdPerson(dt)
        } else {
            move()
        }
    }

    private fun handleLerp(dt: Float) {
        val target = targetPreset ?: return
        lerpTime += dt
        val t = (lerpTime / lerpDuration).coerceIn(0f, 1f)
        
        fov = Interpolation.lerp(startFov, target.fov, t)
        desiredDistance = Interpolation.lerp(startDistance, target.distance, t)
        targetOffset.lerp(target.offset, t)
        
        if (t >= 1f) {
            targetPreset = null
        }
    }

    private fun updateThirdPerson(dt: Float) {
        val rawTarget = target!!
        val targetPos = Vector3f(rawTarget).add(targetOffset)
        
        // Input Handling
        val sensitivity = 0.1f
        val controllerSensitivity = 2.0f
        
        // Mouse Rotation
        if (inputProvider.isCursorDisabled()) {
            yaw += MouseListener.getDx() * sensitivity
            pitch += MouseListener.getDy() * sensitivity
        }
        
        // RS Rotation (Joystick 1)
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > JoystickListener.AXIS_RIGHT_Y) {
                val rsX = axes[JoystickListener.AXIS_RIGHT_X]
                val rsY = axes[JoystickListener.AXIS_RIGHT_Y]
                
                if (Math.abs(rsX) > 0.1f) yaw += rsX * controllerSensitivity
                if (Math.abs(rsY) > 0.1f) pitch += rsY * controllerSensitivity
            }
        }

        if (pitch > 89f) pitch = 89f
        if (pitch < -89f) pitch = -89f

        // Calculate offset
        val horizontalDist = desiredDistance * Math.cos(Math.toRadians(pitch.toDouble())).toFloat()
        val verticalDist = desiredDistance * Math.sin(Math.toRadians(pitch.toDouble())).toFloat()
        
        val offsetX = horizontalDist * Math.sin(Math.toRadians(yaw.toDouble())).toFloat()
        val offsetZ = horizontalDist * Math.cos(Math.toRadians(yaw.toDouble())).toFloat()
        
        val desiredPos = Vector3f(targetPos.x - offsetX, targetPos.y + verticalDist, targetPos.z + offsetZ)
        
        // Clipping
        val finalPos = handleClipping(targetPos, desiredPos)
        position.set(finalPos)
    }

    private fun handleClipping(from: Vector3f, to: Vector3f): Vector3f {
        val scene = com.pafoid.skate.engine.scenes.SceneManager.getCurrentScene()
        if (scene != null) {
            val results = scene.physics3d.rayTest(from, to)
            if (results.isNotEmpty()) {
                var closestFraction = 1.0f
                for (result in results) {
                    if (result.hitFraction < closestFraction) {
                        closestFraction = result.hitFraction
                    }
                }
                
                if (closestFraction < 1.0f) {
                    // Move slightly away from the hit point to avoid near-plane clipping
                    val clippedPos = Vector3f(from).lerp(to, closestFraction * 0.9f)
                    return clippedPos
                }
            }
        }
        return to
    }

    fun move() {
        // Rotation
        if (inputProvider.isCursorDisabled()) {
            val sensitivity = 0.1f
            yaw += MouseListener.getDx() * sensitivity
            pitch += MouseListener.getDy() * sensitivity
            
            // Limit pitch
            if (pitch > 89f) pitch = 89f
            if (pitch < -89f) pitch = -89f
        }

        // Movement
        val speed = 0.1f
        val forward = Vector3f(
            Math.sin(Math.toRadians(yaw.toDouble())).toFloat(),
            -Math.sin(Math.toRadians(pitch.toDouble())).toFloat(),
            -Math.cos(Math.toRadians(yaw.toDouble())).toFloat()
        ).normalize()
        
        val right = Vector3f(
            Math.cos(Math.toRadians(yaw.toDouble())).toFloat(),
            0f,
            Math.sin(Math.toRadians(yaw.toDouble())).toFloat()
        ).normalize()

        if (KeyListener.isKeyPressed(GLFW_KEY_W)) {
            position.add(Vector3f(forward).mul(speed))
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_S)) {
            position.sub(Vector3f(forward).mul(speed))
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_D)) {
            position.add(Vector3f(right).mul(speed))
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_A)) {
            position.sub(Vector3f(right).mul(speed))
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_SPACE)) {
            position.y += speed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            position.y -= speed
        }
    }

    fun createProjectionMatrix(): Matrix4f {
        val projectionMatrix = Matrix4f()
        projectionMatrix.identity()
        
        // We'll use a standard aspect ratio if none is provided, 
        // but ideally this should come from the window
        val aspectRatio = 1920f / 1080f 

        if (isOrthographic) {
            val left = -_projectionSize.x * zoom / 2f
            val right = _projectionSize.x * zoom / 2f
            val bottom = -_projectionSize.y * zoom / 2f
            val top = _projectionSize.y * zoom / 2f
            projectionMatrix.ortho(left, right, bottom, top, nearPlane, farPlane)
        } else {
            projectionMatrix.perspective(Math.toRadians(fov.toDouble()).toFloat() * zoom, aspectRatio, nearPlane, farPlane)
        }

        return projectionMatrix
    }

    fun createViewMatrix(): Matrix4f {
        val viewMatrix = Matrix4f().identity()

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
        pitch = Math.asin(-dir.y.toDouble()).toDegrees().toFloat()
        yaw = Math.atan2(dir.x.toDouble(), -dir.z.toDouble()).toDegrees().toFloat()
    }

    fun screenToRay(screenX: Float, screenY: Float, width: Float, height: Float): com.pafoid.skate.engine.utils.Ray {
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
        
        return com.pafoid.skate.engine.utils.Ray(rayOrigin, rayDirection)
    }

}
