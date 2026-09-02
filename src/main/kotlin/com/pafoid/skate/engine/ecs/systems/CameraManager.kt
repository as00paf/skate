package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.CameraAction
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Matrix4f
import org.joml.Vector3f

class CameraManager(
    eventSystem: EventSystem,
) : System(priority = SystemManager.ExecutionPriority.EARLY) {

    var camera = CameraComponent()
    var transform = Transform()

    init {
        eventSystem.subscribe<CameraAction.SetCamera> { event ->
            camera = event.camera
            event.transform?.let { transform = it }
        }
    }

    override fun init(scene: Scene) {
        super.init(scene)
        val sceneCameras = scene.getAllComponents<CameraComponent>()
        val defaultCamera = sceneCameras.firstOrNull { it.isDefault } ?: sceneCameras.firstOrNull()
        defaultCamera?.let {
            camera = it
            it.gameObject?.getComponent<Transform>()?.let { t -> transform = t }
        }
    }

    override fun update(dt: Float) {
        calculateForwardAndRight()
        calculateProjection()
        calculateView()
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

            view.rotate(transform.rotation.x.toRadiansF(), Vector3f(1f, 0f, 0f))
            view.rotate(transform.rotation.y.toRadiansF(), Vector3f(0f, 1f, 0f))
            view.rotate(transform.rotation.z.toRadiansF(), Vector3f(0f, 0f, 1f))

            val negativeCameraPos = Vector3f(transform.worldMatrix.getTranslation(Vector3f())).negate()
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