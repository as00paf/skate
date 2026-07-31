package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.render.CameraComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.Ray
import org.joml.Vector3f
import kotlin.math.abs

class RotationGizmo(
    inputProvider: InputProvider,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(inputProvider, undoRedoManager) {
    private val radius = 2.0f
    private val hitThreshold = 0.4f
    
    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false
    private var oldTransform: Transform? = null

    fun update(activeGameObject: GameObject?, camera: CameraComponent) {
        activeGameObject?.getComponent<Transform>()?.let { transform ->
            val pos = transform.translation
            val dist = Vector3f(camera.position).distance(pos)
            val dynamicRadius = radius * (dist * 0.1f)
            val dynamicThreshold = hitThreshold * (dist * 0.1f)

            checkInput(activeGameObject, transform, camera, dynamicRadius, dynamicThreshold)

            if (xAxisActive) {
                transform.rotation.x += inputProvider.getMouseDy()
            } else if (yAxisActive) {
                transform.rotation.y += inputProvider.getMouseDx()
            } else if (zAxisActive) {
                transform.rotation.z += inputProvider.getMouseDy()
            }

            with(debugRenderer) {
                drawCircle(pos, dynamicRadius, Vector3f(1f, 0f, 0f), if (xAxisActive || xAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(1f, 0f, 0f)) // X - Red/Yellow
                drawCircle(pos, dynamicRadius, Vector3f(0f, 1f, 0f), if (yAxisActive || yAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 1f, 0f)) // Y - Green/Yellow
                drawCircle(pos, dynamicRadius, Vector3f(0f, 0f, 1f), if (zAxisActive || zAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 0f, 1f)) // Z - Blue/Yellow
            }
        }
    }

    private fun checkInput(
        go: GameObject,
        transform: Transform,
        camera: CameraComponent,
        rad: Float,
        threshold: Float
    ) {
        val pos = transform.translation
        val mouseX = inputProvider.getMouseScreenX()
        val mouseY = inputProvider.getMouseScreenY()
        val viewportSize = inputProvider.getGameViewportSize()
        val ray = camera.screenToRay(mouseX, mouseY, viewportSize.x, viewportSize.y)

        // Reset hover states
        xAxisHot = false
        yAxisHot = false
        zAxisHot = false

        if (rayToCircleDist(ray, pos, Vector3f(1f, 0f, 0f), rad) < threshold) xAxisHot = true
        else if (rayToCircleDist(ray, pos, Vector3f(0f, 1f, 0f), rad) < threshold) yAxisHot = true
        else if (rayToCircleDist(ray, pos, Vector3f(0f, 0f, 1f), rad) < threshold) zAxisHot = true

        if (inputProvider.isLeftMouseButtonDown(true)) {
            if (!xAxisActive && !yAxisActive && !zAxisActive) {
                if (xAxisHot) xAxisActive = true
                else if (yAxisHot) yAxisActive = true
                else if (zAxisHot) zAxisActive = true

                if (xAxisActive || yAxisActive || zAxisActive) {
                    oldTransform = Transform().apply { copyFrom(transform) }
                }
            }
        } else {
            if (xAxisActive || yAxisActive || zAxisActive) {
                // We were dragging and just stopped
                oldTransform?.let { old ->
                    if (old != transform) {
                        undoRedoManager.pushCommand(TransformCommand(go, old, transform))
                    }
                }
                oldTransform = null
            }
            xAxisActive = false
            yAxisActive = false
            zAxisActive = false
        }
    }

    private fun rayToCircleDist(ray: Ray, center: Vector3f, axis: Vector3f, rad: Float): Float {
        // Plane intersection
        val denom = axis.dot(ray.direction)
        if (abs(denom) < 0.0001f) return Float.MAX_VALUE

        val t = Vector3f(center).sub(ray.origin).dot(axis) / denom
        if (t < 0) return Float.MAX_VALUE

        val hitPoint = Vector3f(ray.origin).add(Vector3f(ray.direction).mul(t))
        val distToCenter = hitPoint.distance(center)
        
        return abs(distToCenter - rad)
    }
}
