package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Vector3f
import kotlin.math.abs

class TranslateGizmo(
    engine: Engine,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(engine, undoRedoManager) {
    private val arrowLength = 2.0f
    private val coneSize = 0.3f
    private val hitThreshold = 0.3f
    
    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false

    private var oldTransform: Transform? = null

    var sensitivity = 0.0005f

    fun update(activeGameObject: GameObject?, camera: CameraComponent) {
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
                transform.translation.x += inputProvider.mouseDistanceFrom(
                    pos,
                    camera,
                    Vector3f(1f, 0f, 0f)
                ) * sensitivity
            } else if (yAxisActive) {
                transform.translation.y += inputProvider.mouseDistanceFrom(
                    pos,
                    camera,
                    Vector3f(0f, 1f, 0f)
                ) * sensitivity
            } else if (zAxisActive) {
                transform.translation.z += inputProvider.mouseDistanceFrom(
                    pos,
                    camera,
                    Vector3f(0f, 0f, 1f)
                ) * sensitivity
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

    private fun checkInput(go: GameObject, camera: CameraComponent, length: Float, threshold: Float) {
        val transform = go.getComponent<Transform>() ?: return
        val pos = transform.translation
        val ray = inputProvider.screenToRay(camera)

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
                        sceneManager.currentScene?.isDirty = true
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
}