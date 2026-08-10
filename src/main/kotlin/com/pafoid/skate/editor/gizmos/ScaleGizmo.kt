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

class ScaleGizmo(
    engine: Engine,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(engine, undoRedoManager) {
    private val handleLength = 2.0f
    private val boxSize = 0.3f
    private val hitThreshold = 0.3f

    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false

    private var oldTransform: Transform? = null

    var sensitivity = 0.001f

    fun update(go: GameObject?, camera: CameraComponent) {
        go?.getComponent<Transform>()?.let { transform ->
            val pos = transform.translation

            val dist = Vector3f(camera.position).distance(pos)
            val dynamicLength = handleLength * (dist * 0.1f)
            val dynamicBoxSize = boxSize * (dist * 0.1f)
            val dynamicThreshold = hitThreshold * (dist * 0.1f)

            checkInput(pos, camera, dynamicLength, dynamicThreshold)

            if (inputProvider.isLeftMouseButtonDown(true)) {
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
                transform.scale.x += inputProvider.mouseDistanceFrom(pos, camera, Vector3f(1f, 0f, 0f)) * sensitivity
                transform.scale.x = transform.scale.x.coerceAtLeast(0.01f)
            } else if (yAxisActive) {
                transform.scale.y += inputProvider.mouseDistanceFrom(pos, camera, Vector3f(0f, 1f, 0f)) * sensitivity
                transform.scale.y = transform.scale.y.coerceAtLeast(0.01f)
            } else if (zAxisActive) {
                transform.scale.z += inputProvider.mouseDistanceFrom(pos, camera, Vector3f(0f, 0f, 1f)) * sensitivity
                transform.scale.z = transform.scale.z.coerceAtLeast(0.01f)
            }

            drawHandle(pos, Vector3f(1f, 0f, 0f), if (xAxisActive || xAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(1f, 0f, 0f), dynamicLength, dynamicBoxSize)
            drawHandle(pos, Vector3f(0f, 1f, 0f), if (yAxisActive || yAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 1f, 0f), dynamicLength, dynamicBoxSize)
            drawHandle(pos, Vector3f(0f, 0f, 1f), if (zAxisActive || zAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 0f, 1f), dynamicLength, dynamicBoxSize)
        }
    }

    private fun checkInput(pos: Vector3f, camera: CameraComponent, length: Float, threshold: Float) {
        val ray = inputProvider.screenToRay(camera)

        // Reset hover states
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
}