package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.AXIS_RIGHT_X
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.AXIS_RIGHT_Y
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.toRadians
import com.pafoid.skate.engine.utils.toDegrees
import com.pafoid.skate.engine.utils.Interpolation
import com.pafoid.skate.engine.utils.Ray
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.*
import kotlin.getValue
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class Camera(
    val position: Vector3f = Vector3f(),
    var pitch: Float = 0f,
    var yaw: Float = 0f,
    var roll: Float = 0f,
    var isOrthographic: Boolean = false
): KoinComponent {
    private val inputProvider: IInputProvider by inject()
    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()

    var fov = 45f
    var nearPlane = 0.1f
    var farPlane = 1000f
    
    var projectionSize = Vector2f(32f, 18f) // Default 16:9 units
    var zoom = 1.0f

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
            yaw += mouseListener.getDx() * sensitivity
            pitch += mouseListener.getDy() * sensitivity
        }
        
        // RS Rotation (Joystick 1)
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > AXIS_RIGHT_Y) {
                val rsX = axes[AXIS_RIGHT_X]
                val rsY = axes[AXIS_RIGHT_Y]
                
                if (abs(rsX) > 0.1f) yaw += rsX * controllerSensitivity
                if (abs(rsY) > 0.1f) pitch += rsY * controllerSensitivity
            }
        }

        if (pitch > 89f) pitch = 89f
        if (pitch < -89f) pitch = -89f

        // Calculate offset
        val horizontalDist = desiredDistance * cos(Math.toRadians(pitch.toDouble())).toFloat()
        val verticalDist = desiredDistance * sin(Math.toRadians(pitch.toDouble())).toFloat()
        
        val offsetX = horizontalDist * sin(Math.toRadians(yaw.toDouble())).toFloat()
        val offsetZ = horizontalDist * cos(Math.toRadians(yaw.toDouble())).toFloat()
        
        val desiredPos = Vector3f(targetPos.x - offsetX, targetPos.y + verticalDist, targetPos.z + offsetZ)
        
        // Clipping
        val finalPos = handleClipping(targetPos, desiredPos)
        position.set(finalPos)
    }

    private fun handleClipping(from: Vector3f, to: Vector3f): Vector3f {
        val scene = SceneManager.getCurrentScene()
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
            yaw += mouseListener.getDx() * sensitivity
            pitch += mouseListener.getDy() * sensitivity
            
            // Limit pitch
            if (pitch > 89f) pitch = 89f
            if (pitch < -89f) pitch = -89f
        }

        // Movement
        val speed = 0.1f
        val forward = Vector3f(
            sin(Math.toRadians(yaw.toDouble())).toFloat(),
            -sin(Math.toRadians(pitch.toDouble())).toFloat(),
            -cos(Math.toRadians(yaw.toDouble())).toFloat()
        ).normalize()
        
        val right = Vector3f(
            cos(Math.toRadians(yaw.toDouble())).toFloat(),
            0f,
            sin(Math.toRadians(yaw.toDouble())).toFloat()
        ).normalize()

        if (keyListener.isKeyPressed(GLFW_KEY_W)) {
            position.add(Vector3f(forward).mul(speed))
        }
        if (keyListener.isKeyPressed(GLFW_KEY_S)) {
            position.sub(Vector3f(forward).mul(speed))
        }
        if (keyListener.isKeyPressed(GLFW_KEY_D)) {
            position.add(Vector3f(right).mul(speed))
        }
        if (keyListener.isKeyPressed(GLFW_KEY_A)) {
            position.sub(Vector3f(right).mul(speed))
        }
        if (keyListener.isKeyPressed(GLFW_KEY_SPACE)) {
            position.y += speed
        }
        if (keyListener.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
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

}
