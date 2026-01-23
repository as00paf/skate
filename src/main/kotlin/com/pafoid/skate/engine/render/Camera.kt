package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.utils.toRadians
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import kotlin.math.tan


const val FOV = 135f
const val NEAR_PLANE = 0.1f
const val FAR_PLANE = 1000f

class Camera(
    val position: Vector3f = Vector3f(),
    var pitch: Float = 0f,
    var yaw: Float = 0f,
    var roll: Float = 0f
) {

    fun move() {
        if (KeyListener.isKeyPressed(GLFW_KEY_W)) {
            position.z -= 0.02f
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_S)) {
            position.z += 0.02f
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_D)) {
            position.x += 0.02f
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_A)) {
            position.x -= 0.02f
        }
    }

    fun createProjectionMatrix(): Matrix4f {
        val screenWidth = 1854//1920
        val screenHeight = 1057//1080
        val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
        val yScale = (1f / tan(Math.toRadians((FOV / 2f).toDouble())) * aspectRatio).toFloat()
        val xScale = yScale / aspectRatio
        val viewDistance = FAR_PLANE - NEAR_PLANE

        val projectionMatrix = Matrix4f()
        projectionMatrix.m00(xScale)
        projectionMatrix.m11(yScale)
        projectionMatrix.m22(-((FAR_PLANE + NEAR_PLANE) / viewDistance))
        projectionMatrix.m23(-1f)
        projectionMatrix.m32(-((2 * FAR_PLANE * NEAR_PLANE) / viewDistance))
        projectionMatrix.m33(0f)

        return projectionMatrix
    }

    fun createViewMatrix(): Matrix4f {
        val viewMatrix = Matrix4f().identity()

        viewMatrix.rotate(pitch.toRadians(), Vector3f(1f, 0f, 0f), viewMatrix)
        viewMatrix.rotate(yaw.toRadians(), Vector3f(0f, 1f, 0f), viewMatrix)
        viewMatrix.rotate(roll.toRadians(), Vector3f(0f, 0f, 1f), viewMatrix)
        val negativeCameraPos = Vector3f(position).negate()
        viewMatrix.translate(negativeCameraPos, viewMatrix)

        return viewMatrix
    }

}