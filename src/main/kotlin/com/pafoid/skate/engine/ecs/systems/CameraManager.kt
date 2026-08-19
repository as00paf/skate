package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.engine.events.CameraAction
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.toRadians
import org.joml.Matrix4f
import org.joml.Vector3f

class CameraManager(
    eventSystem: EventSystem,
) : System(priority = SystemManager.ExecutionPriority.EARLY) {

    var camera: CameraComponent = CameraComponent().also { it.position.set(Vector3f(0f, 5f, 20f)) }

    init {
        eventSystem.subscribe<CameraAction.SetCamera> { event ->
            camera = event.camera
        }
    }

    override fun init(scene: Scene) {
        super.init(scene)
        val sceneCameras = scene.getAllComponents<CameraComponent>()
        val defaultCamera = sceneCameras.firstOrNull { it.isDefault } ?: sceneCameras.firstOrNull()
        defaultCamera?.let { camera = it }
    }

    override fun update(dt: Float) {
        if (camera.gameObject != null) calculateWorldPosition()
        calculateForwardAndRight()
        calculateProjection()
        calculateView()
    }

    private val matrix = Matrix4f()
    private val newPos = Vector3f()

    //TODO: move to new TransformSystem
    private fun calculateWorldPosition() {
        matrix.identity()
        matrix.translation(camera.position)
        matrix.rotation(Math.toRadians(camera.pitch.toDouble()).toFloat(), Vector3f(1f, 0f, 0f))
        matrix.rotation(Math.toRadians(camera.yaw.toDouble()).toFloat(), Vector3f(0f, 1f, 0f))
        matrix.rotation(Math.toRadians(camera.roll.toDouble()).toFloat(), Vector3f(0f, 0f, 1f))
        camera.gameObject?.parent?.let { parent ->
            val parentTransform = parent.getComponent<Transform>()
            val parentMatrix =
                parentTransform?.toWorldMatrix() ?: Matrix4f().identity() // fallback for backward compatibility
            parentMatrix.mul(matrix, matrix)
        }
        matrix.getTranslation(newPos)
        //camera.position.set(newPos)
    }

    private fun calculateProjection() {
        with(camera) {
            projection.identity()

            if (isOrthographic) {
                val left = -viewportWidth * zoom / 2f
                val right = viewportWidth * zoom / 2f
                val bottom = -viewportHeight * zoom / 2f
                val top = viewportHeight * zoom / 2f
                projection.ortho(left, right, bottom, top, nearPlane, farPlane)
            } else {
                projection.perspective(
                    Math.toRadians(fov.toDouble()).toFloat() * zoom,
                    aspectRatio,
                    nearPlane,
                    farPlane
                )
            }
            Matrix4f(projection).invert(inverseProjection)
        }
    }

    private fun calculateView() {
        with(camera) {
            view.identity()

            view.rotate(pitch.toRadians(), Vector3f(1f, 0f, 0f))
            view.rotate(yaw.toRadians(), Vector3f(0f, 1f, 0f))
            view.rotate(roll.toRadians(), Vector3f(0f, 0f, 1f))

            val negativeCameraPos = Vector3f(position).negate()
            view.translate(negativeCameraPos)
            Matrix4f(view).invert(inverseView)
        }
    }

    private fun calculateForwardAndRight(): Pair<Vector3f, Vector3f> {
        with(camera) {
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
    }
}