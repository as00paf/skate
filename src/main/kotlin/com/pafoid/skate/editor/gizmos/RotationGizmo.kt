package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.Ray
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import kotlin.math.abs

class RotationGizmo(
    mouseListener: MouseListener,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(mouseListener, undoRedoManager), KoinComponent {
    private val radius = 2.0f
    private val hitThreshold = 0.4f
    
    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false

    fun update(camera: Camera) {
        activeGameObject?.getComponent<Transform>()?.let { transform ->
            val pos = transform.translation

            val dist = Vector3f(camera.position).distance(pos)
            val dynamicRadius = radius * (dist * 0.1f)
            val dynamicThreshold = hitThreshold * (dist * 0.1f)

            checkInput(camera, dynamicRadius, dynamicThreshold)

            if (xAxisActive) {
                transform.rotation.x += mouseListener.getScreenDy()
            } else if (yAxisActive) {
                transform.rotation.y += mouseListener.getScreenDx()
            } else if (zAxisActive) {
                transform.rotation.z += mouseListener.getScreenDy()
            }

            with(debugRenderer) {
                drawCircle(pos, dynamicRadius, Vector3f(1f, 0f, 0f), if (xAxisActive || xAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(1f, 0f, 0f)) // X - Red/Yellow
                drawCircle(pos, dynamicRadius, Vector3f(0f, 1f, 0f), if (yAxisActive || yAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 1f, 0f)) // Y - Green/Yellow
                drawCircle(pos, dynamicRadius, Vector3f(0f, 0f, 1f), if (zAxisActive || zAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 0f, 1f)) // Z - Blue/Yellow
            }
        }
    }

    override fun isHot(): Boolean = xAxisHot || yAxisHot || zAxisHot

    private fun checkInput(camera: Camera, rad: Float, threshold: Float) {
        val go = activeGameObject ?: return
        val transform = go.getComponent<Transform>() ?: return
        val pos = transform.translation

        val mouseX = mouseListener.getScreenX()
        val mouseY = mouseListener.getScreenY()
        val viewportSize = mouseListener.getGameViewportSize()
        val ray = camera.screenToRay(mouseX, mouseY, viewportSize.x, viewportSize.y)

        // Reset hover states
        xAxisHot = false
        yAxisHot = false
        zAxisHot = false

        if (rayToCircleDist(ray, pos, Vector3f(1f, 0f, 0f), rad) < threshold) xAxisHot = true
        else if (rayToCircleDist(ray, pos, Vector3f(0f, 1f, 0f), rad) < threshold) yAxisHot = true
        else if (rayToCircleDist(ray, pos, Vector3f(0f, 0f, 1f), rad) < threshold) zAxisHot = true

        if (mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT, true)) {
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
