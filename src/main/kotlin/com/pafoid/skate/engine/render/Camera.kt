package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.utils.toRadians
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*

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

    fun addZoom(value: Float) {
        zoom += value
        if (zoom <= 0.1f) {
            zoom = 0.1f
        }
    }

    fun getProjectionSize(): Vector2f {
        return _projectionSize
    }

    fun move() {
        // Rotation
        if (glfwGetInputMode(glfwGetCurrentContext(), GLFW_CURSOR) == GLFW_CURSOR_DISABLED) {
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
        if (KeyListener.isKeyPressed(GLFW_KEY_E)) {
            position.y += speed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_Q)) {
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

}