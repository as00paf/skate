package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.Ray
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs

class TranslateGizmo(
    inputProvider: InputProvider,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(inputProvider, undoRedoManager) {
    private val arrowLength = 2.0f
    private val coneSize = 0.3f
    private val hitThreshold = 0.3f
    
    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false

    private var oldTransform: Transform? = null

    fun update(activeGameObject: GameObject?, camera: Camera) {
        val go = activeGameObject ?: return
        go.getComponent<Transform>()?.let{ transform ->
            val pos = transform.translation
            val dist = Vector3f(camera.position).distance(pos)
            val dynamicArrowLength = arrowLength * (dist * 0.1f)
            val dynamicConeSize = coneSize * (dist * 0.1f)
            val dynamicHitThreshold = hitThreshold * (dist * 0.1f)

            checkInput(go, camera, dynamicArrowLength, dynamicHitThreshold)

            // TODO: fix snapping
            if (xAxisActive) {
                transform.translation.x += calculateDelta(go, camera, Vector3f(1f, 0f, 0f))
                //if (go.getComponent<ModularTile>() != null) transform.translation.x = (transform.translation.x / 2.0f).roundToInt() * 2.0f
            } else if (yAxisActive) {
                transform.translation.y += calculateDelta(go, camera, Vector3f(0f, 1f, 0f))
                //if (go.getComponent<ModularTile>() != null) transform.translation.y = (transform.translation.y / 2.0f).roundToInt() * 2.0f
            } else if (zAxisActive) {
                transform.translation.z += calculateDelta(go, camera, Vector3f(0f, 0f, 1f))
                //if (go.getComponent<ModularTile>() != null) transform.translation.z = (transform.translation.z / 2.0f).roundToInt() * 2.0f
            }

            drawArrow(pos, Vector3f(1f, 0f, 0f), if (xAxisActive || xAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(1f, 0f, 0f), dynamicArrowLength, dynamicConeSize) // X - Red/Yellow
            drawArrow(pos, Vector3f(0f, 1f, 0f), if (yAxisActive || yAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 1f, 0f), dynamicArrowLength, dynamicConeSize) // Y - Green/Yellow
            drawArrow(pos, Vector3f(0f, 0f, 1f), if (zAxisActive || zAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 0f, 1f), dynamicArrowLength, dynamicConeSize) // Z - Blue/Yellow
        }
    }

    private fun drawArrow(origin: Vector3f, direction: Vector3f, color: Vector3f, length: Float, cSize: Float) {
        val end = Vector3f(origin).add(Vector3f(direction).mul(length))
        debugRenderer.addLine3D(origin, end, color)

        val ortho1 = if (abs(direction.x) > 0.9f) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)
        val ortho2 = Vector3f(direction).cross(ortho1).normalize().mul(cSize)
        val ortho3 = Vector3f(direction).cross(ortho2).normalize().mul(cSize)
        
        val base = Vector3f(end).sub(Vector3f(direction).mul(cSize))
        val p1 = Vector3f(base).add(ortho2)
        val p2 = Vector3f(base).sub(ortho2)
        val p3 = Vector3f(base).add(ortho3)
        val p4 = Vector3f(base).sub(ortho3)

        debugRenderer.addTriangle3D(end, p1, p3, color)
        debugRenderer.addTriangle3D(end, p3, p2, color)
        debugRenderer.addTriangle3D(end, p2, p4, color)
        debugRenderer.addTriangle3D(end, p4, p1, color)

        debugRenderer.addTriangle3D(p1, p2, p3, color)
        debugRenderer.addTriangle3D(p1, p2, p4, color)
    }

    private fun checkInput(go: GameObject, camera: Camera, length: Float, threshold: Float) {
        val transform = go.getComponent<Transform>() ?: return
        val pos = transform.translation

        val mouseX = inputProvider.getMouseScreenX()
        val mouseY = inputProvider.getMouseScreenY()
        val viewportSize = inputProvider.getGameViewportSize()
        val ray = camera.screenToRay(mouseX, mouseY, viewportSize.x, viewportSize.y)

        xAxisHot = false
        yAxisHot = false
        zAxisHot = false

        if (rayToLineDist(ray, pos, Vector3f(1f, 0f, 0f), length) < threshold) xAxisHot = true
        else if (rayToLineDist(ray, pos, Vector3f(0f, 1f, 0f), length) < threshold) yAxisHot = true
        else if (rayToLineDist(ray, pos, Vector3f(0f, 0f, 1f), length) < threshold) zAxisHot = true

        if (inputProvider.isLeftMouseButtonDown(true)) {
            if (!xAxisActive && !yAxisActive && !zAxisActive) {
                if (xAxisHot) xAxisActive = true
                else if (yAxisHot) yAxisActive = true
                else if (zAxisHot) zAxisActive = true

                if (xAxisActive || yAxisActive || zAxisActive) {
                    oldTransform = Transform().apply { copyFrom(transform) }
                    println("Start dragging ${transform.translation}")
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
                println("Stop dragging ${transform.translation}")
                oldTransform = null
            }
            xAxisActive = false
            yAxisActive = false
            zAxisActive = false
        }
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

    private fun calculateDelta(activeGameObject: GameObject?, camera: Camera, axis: Vector3f): Float {
        val view = camera.createViewMatrix()
        val proj = camera.createProjectionMatrix()
        val viewportSize = inputProvider.getGameViewportSize()

        val go = activeGameObject ?: return 0f
        val transform = go.getComponent<Transform>() ?: return 0f
        val origin = Vector3f(transform.translation)
        val p2 = Vector3f(origin).add(axis)

        val s1 = worldToScreen(origin, view, proj, viewportSize.x, viewportSize.y)
        val s2 = worldToScreen(p2, view, proj, viewportSize.x, viewportSize.y)

        val axisScreen = s2.sub(s1)
        if (axisScreen.lengthSquared() < 0.0001f) return 0f

        val axisScreenDir = axisScreen.normalize()
        val mouseDelta = Vector2f(inputProvider.getMouseDx(), inputProvider.getMouseDy())

        val projection = mouseDelta.dot(axisScreenDir)
        val dist = Vector3f(camera.position).distance(origin)
        val sensitivity = 0.0005f * dist

        return projection * sensitivity
    }
    
    private fun worldToScreen(worldPos: Vector3f, view: Matrix4f, proj: Matrix4f, width: Float, height: Float): Vector2f {
        val coords = Vector4f(worldPos, 1.0f)
        view.transform(coords)
        proj.transform(coords)
        
        if (coords.w == 0f) return Vector2f()
        
        coords.x /= coords.w
        coords.y /= coords.w
        
        val x = (coords.x + 1) * width / 2f
        val y = (1 - coords.y) * height / 2f
        
        return Vector2f(x, y)
    }
}