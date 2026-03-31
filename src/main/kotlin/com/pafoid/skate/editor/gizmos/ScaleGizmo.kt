package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.TransformCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.Ray
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.component.KoinComponent
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT

class ScaleGizmo(
    mouseListener: MouseListener,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(mouseListener, undoRedoManager), KoinComponent {
    private val handleLength = 2.0f
    private val boxSize = 0.3f
    private val hitThreshold = 0.3f

    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false

    override fun editorUpdate(dt: Float) {
        super.editorUpdate(dt)
        val go = activeGameObject ?: return
        go.getComponent<Transform>()?.let { transform ->
            val pos = transform.translation

            val dist = Vector3f(scene.camera.position).distance(pos)
            val dynamicLength = handleLength * (dist * 0.1f)
            val dynamicBoxSize = boxSize * (dist * 0.1f)
            val dynamicThreshold = hitThreshold * (dist * 0.1f)

            checkInput(dynamicLength, dynamicThreshold)

            if (mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT, true)) {
                if (xAxisActive || yAxisActive || zAxisActive) {
                    if (oldTransform == null) {
                        oldTransform = Transform().apply { copyFrom(transform) }
                    }
                }
            } else {
                oldTransform?.let { old ->
                    if (old != transform) {
                        undoRedoManager.pushCommand(TransformCommand(go, old, transform))
                    }
                }
                oldTransform = null
            }

            if (xAxisActive) {
                transform.scale.x += calculateDelta(Vector3f(1f, 0f, 0f))
                transform.scale.x = transform.scale.x.coerceAtLeast(0.01f)
            } else if (yAxisActive) {
                transform.scale.y += calculateDelta(Vector3f(0f, 1f, 0f))
                transform.scale.y = transform.scale.y.coerceAtLeast(0.01f)
            } else if (zAxisActive) {
                transform.scale.z += calculateDelta(Vector3f(0f, 0f, 1f))
                transform.scale.z = transform.scale.z.coerceAtLeast(0.01f)
            }

            drawHandle(pos, Vector3f(1f, 0f, 0f), if (xAxisActive || xAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(1f, 0f, 0f), dynamicLength, dynamicBoxSize)
            drawHandle(pos, Vector3f(0f, 1f, 0f), if (yAxisActive || yAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 1f, 0f), dynamicLength, dynamicBoxSize)
            drawHandle(pos, Vector3f(0f, 0f, 1f), if (zAxisActive || zAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 0f, 1f), dynamicLength, dynamicBoxSize)
        }
    }

    override fun isHot(): Boolean = xAxisHot || yAxisHot || zAxisHot

    private fun checkInput(length: Float, threshold: Float) {
        val go = activeGameObject ?: return
        val transform = go.getComponent<Transform>() ?: return
        val pos = transform.translation

        val mouseX = mouseListener.getScreenX()
        val mouseY = mouseListener.getScreenY()
        val viewportSize = mouseListener.getGameViewportSize()
        val ray = scene.camera.screenToRay(mouseX, mouseY, viewportSize.x, viewportSize.y)

        // Reset hover states
        xAxisHot = false
        yAxisHot = false
        zAxisHot = false

        if (rayToLineDist(ray, pos, Vector3f(1f, 0f, 0f), length) < threshold) xAxisHot = true
        else if (rayToLineDist(ray, pos, Vector3f(0f, 1f, 0f), length) < threshold) yAxisHot = true
        else if (rayToLineDist(ray, pos, Vector3f(0f, 0f, 1f), length) < threshold) zAxisHot = true

        if (mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT, true)) {
            if (!xAxisActive && !yAxisActive && !zAxisActive) {
                if (xAxisHot) xAxisActive = true
                else if (yAxisHot) yAxisActive = true
                else if (zAxisHot) zAxisActive = true
            }
        } else {
            xAxisActive = false
            yAxisActive = false
            zAxisActive = false
        }
    }

    private fun drawHandle(origin: Vector3f, direction: Vector3f, color: Vector3f, length: Float, size: Float) {
        val end = Vector3f(origin).add(Vector3f(direction).mul(length))
        debugRenderer.addLine3D(origin, end, color)
        
        // Solid Cube at end
        val h = size / 2f
        val center = end
        
        val v1 = Vector3f(center).add(-h, -h, -h)
        val v2 = Vector3f(center).add( h, -h, -h)
        val v3 = Vector3f(center).add( h,  h, -h)
        val v4 = Vector3f(center).add(-h,  h, -h)
        val v5 = Vector3f(center).add(-h, -h,  h)
        val v6 = Vector3f(center).add( h, -h,  h)
        val v7 = Vector3f(center).add( h,  h,  h)
        val v8 = Vector3f(center).add(-h,  h,  h)

        // Front
        debugRenderer.addTriangle3D(v5, v6, v7, color)
        debugRenderer.addTriangle3D(v5, v7, v8, color)
        // Back
        debugRenderer.addTriangle3D(v1, v3, v2, color)
        debugRenderer.addTriangle3D(v1, v4, v3, color)
        // Top
        debugRenderer.addTriangle3D(v4, v7, v3, color)
        debugRenderer.addTriangle3D(v4, v8, v7, color)
        // Bottom
        debugRenderer.addTriangle3D(v1, v2, v6, color)
        debugRenderer.addTriangle3D(v1, v6, v5, color)
        // Left
        debugRenderer.addTriangle3D(v1, v5, v8, color)
        debugRenderer.addTriangle3D(v1, v8, v4, color)
        // Right
        debugRenderer.addTriangle3D(v2, v3, v7, color)
        debugRenderer.addTriangle3D(v2, v7, v6, color)
    }

    private fun rayToLineDist(ray: Ray, origin: Vector3f, direction: Vector3f, length: Float): Float {
        var minDist = Float.MAX_VALUE
        for (i in 0..10) {
            val p = Vector3f(origin).add(Vector3f(direction).mul(length * (i/10f)))
            val dist = ray.distanceToPoint(p)
            if (dist < minDist) minDist = dist
        }
        return minDist
    }

    private fun calculateDelta(axis: Vector3f): Float {
        val camera = scene.camera
        val view = camera.createViewMatrix()
        val proj = camera.createProjectionMatrix()
        val viewportSize = mouseListener.getGameViewportSize()

        val go = activeGameObject ?: return 0f
        val transform = go.getComponent<Transform>() ?: return 0f
        val origin = Vector3f(transform.translation)
        val p2 = Vector3f(origin).add(axis)

        val s1 = worldToScreen(origin, view, proj, viewportSize.x, viewportSize.y)
        val s2 = worldToScreen(p2, view, proj, viewportSize.x, viewportSize.y)

        val axisScreen = s2.sub(s1)
        // Guard against NaN: axis is perpendicular to camera or object scale is zero
        if (axisScreen.lengthSquared() < 0.0001f) return 0f

        val axisScreenDir = axisScreen.normalize()
        val mouseDelta = Vector2f(mouseListener.getScreenDx(), mouseListener.getScreenDy())

        val projection = mouseDelta.dot(axisScreenDir)
        val dist = Vector3f(camera.position).distance(origin)
        val sensitivity = 0.001f * dist

        return projection * sensitivity
    }
    
    private fun worldToScreen(worldPos: Vector3f, view: Matrix4f, proj: Matrix4f, width: Float, height: Float): Vector2f {
        val coords = Vector4f(worldPos, 1.0f)
        view.transform(coords)
        proj.transform(coords)
        
        if (coords.w == 0f) return Vector2f()
        
        val x = (coords.x + 1) * width / 2f
        val y = (1 - coords.y) * height / 2f
        
        return Vector2f(x, y)
    }
}