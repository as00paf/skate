package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.controls.KeyListener
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
    
    // Orthographic specific
    var projectionSize = Vector2f(32f, 18f) // Default 16:9 units

    fun move() {
        val speed = 0.1f
        if (KeyListener.isKeyPressed(GLFW_KEY_W)) {
            position.z -= speed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_S)) {
            position.z += speed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_D)) {
            position.x += speed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_A)) {
            position.x -= speed
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
            val left = -projectionSize.x / 2f
            val right = projectionSize.x / 2f
            val bottom = -projectionSize.y / 2f
            val top = projectionSize.y / 2f
            projectionMatrix.ortho(left, right, bottom, top, nearPlane, farPlane)
        } else {
            projectionMatrix.perspective(Math.toRadians(fov.toDouble()).toFloat(), aspectRatio, nearPlane, farPlane)
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

}