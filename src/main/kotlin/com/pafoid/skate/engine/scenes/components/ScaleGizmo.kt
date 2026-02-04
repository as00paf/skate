package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import kotlin.math.abs

class ScaleGizmo(sceneManager: SceneManager): Gizmo(sceneManager), KoinComponent {
    private val debugDraw: DebugDraw by inject()
    private val mouseListener: MouseListener by inject()

    private val handleLength = 2.0f
    private val boxSize = 0.3f
    private val hitThreshold = 0.3f

    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false

    override fun editorUpdate(dt: Float) {
        super.editorUpdate(dt)
        activeGameObject?.let { go ->
            val pos = go.transform.translation
            val scene = sceneManager.currentScene ?: return

            val dist = Vector3f(scene.camera.position).distance(pos)
            val dynamicLength = handleLength * (dist * 0.1f)
            val dynamicBoxSize = boxSize * (dist * 0.1f)
            val dynamicThreshold = hitThreshold * (dist * 0.1f)

            checkInput(dynamicLength, dynamicThreshold)

            if (mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT, true)) {
                if (xAxisActive || yAxisActive || zAxisActive) {
                    if (oldTransform == null) {
                        oldTransform = Transform().apply { copyFrom(go.transform) }
                    }
                }
            } else {
                oldTransform?.let { old ->
                    if (old != go.transform) {
                        undoRedoManager.pushCommand(com.pafoid.skate.engine.editor.TransformCommand(go, old, go.transform))
                    }
                }
                oldTransform = null
            }

            if (xAxisActive) {
                go.transform.scale.x += calculateDelta(Vector3f(1f, 0f, 0f))
            } else if (yAxisActive) {
                go.transform.scale.y += calculateDelta(Vector3f(0f, 1f, 0f))
            } else if (zAxisActive) {
                go.transform.scale.z += calculateDelta(Vector3f(0f, 0f, 1f))
            }

            // Draw Handles
            drawHandle(pos, Vector3f(1f, 0f, 0f), if (xAxisActive || xAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(1f, 0f, 0f), dynamicLength, dynamicBoxSize)
            drawHandle(pos, Vector3f(0f, 1f, 0f), if (yAxisActive || yAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 1f, 0f), dynamicLength, dynamicBoxSize)
            drawHandle(pos, Vector3f(0f, 0f, 1f), if (zAxisActive || zAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 0f, 1f), dynamicLength, dynamicBoxSize)
        }
    }

    override fun isHot(): Boolean = xAxisHot || yAxisHot || zAxisHot

    private fun checkInput(length: Float, threshold: Float) {
        val scene = sceneManager.currentScene ?: return
        val go = activeGameObject ?: return
        val pos = go.transform.translation
        
        val mouseX = mouseListener.getScreenX()
        val mouseY = mouseListener.getScreenY()
        val ray = scene.camera.screenToRay(mouseX, mouseY, 1920f, 1080f)

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
        debugDraw.addLine3D(origin, end, color)
        
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
        debugDraw.addTriangle3D(v5, v6, v7, color)
        debugDraw.addTriangle3D(v5, v7, v8, color)
        // Back
        debugDraw.addTriangle3D(v1, v3, v2, color)
        debugDraw.addTriangle3D(v1, v4, v3, color)
        // Top
        debugDraw.addTriangle3D(v4, v7, v3, color)
        debugDraw.addTriangle3D(v4, v8, v7, color)
        // Bottom
        debugDraw.addTriangle3D(v1, v2, v6, color)
        debugDraw.addTriangle3D(v1, v6, v5, color)
        // Left
        debugDraw.addTriangle3D(v1, v5, v8, color)
        debugDraw.addTriangle3D(v1, v8, v4, color)
        // Right
        debugDraw.addTriangle3D(v2, v3, v7, color)
        debugDraw.addTriangle3D(v2, v7, v6, color)
    }

    private fun rayToLineDist(ray: com.pafoid.skate.engine.utils.Ray, origin: Vector3f, direction: Vector3f, length: Float): Float {
        var minDist = Float.MAX_VALUE
        for (i in 0..10) {
            val p = Vector3f(origin).add(Vector3f(direction).mul(length * (i/10f)))
            val dist = ray.distanceToPoint(p)
            if (dist < minDist) minDist = dist
        }
        return minDist
    }

    private fun calculateDelta(axis: Vector3f): Float {
        val scene = sceneManager.currentScene ?: return 0f
        val camera = scene.camera
        val view = camera.createViewMatrix()
        val proj = camera.createProjectionMatrix()
        
        val go = activeGameObject ?: return 0f
        val origin = Vector3f(go.transform.translation)
        val p2 = Vector3f(origin).add(axis)
        
        val s1 = worldToScreen(origin, view, proj, 1920f, 1080f)
        val s2 = worldToScreen(p2, view, proj, 1920f, 1080f)
        
        val axisScreenDir = s2.sub(s1).normalize()
        val mouseDelta = Vector2f(mouseListener.getDx(), -mouseListener.getDy())
        
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