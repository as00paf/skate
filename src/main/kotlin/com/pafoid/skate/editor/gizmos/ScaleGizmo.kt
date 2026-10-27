package com.pafoid.skate.editor.gizmos

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
    private val handleLength = 1.0f
    private val boxSize = 0.1f
    private val hitThreshold = 0.1f

    /** 0..2 axis handles, 3 = uniform handle at the pivot. */
    private var hot = -1
    private var active = -1
    private var dragAxisDir = Vector3f()
    private var dragPivot = Vector3f()
    private var dragStartParam = 0f
    private var dragLength = 1f
    private var uniformFactor = 1f

    var localSpace = true
    /** Scale factor snap step; 0 disables snapping. */
    var snapStep = 0f

    private val axisColors = arrayOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))
    private val highlight = Vector3f(1f, 1f, 0f)

    fun update(targets: List<GameObject>, camera: CameraComponent, cameraPosition: Vector3f) {
        val transforms = targets.mapNotNull { it.getComponent<Transform>() }
        if (transforms.isEmpty()) return
        val pivot = if (active >= 0) dragPivot else GizmoMath.pivot(transforms.map { it.translation })
        val fov = Math.toRadians(camera.fov.toDouble()).toFloat()
        val size = GizmoMath.screenSpaceScale(cameraPosition.distance(pivot), fov)
        val length = handleLength * size
        val axes = GizmoMath.axes(transforms.first().rotation, localSpace)
        val ray = inputProvider.screenToRay(camera)

        if (active < 0) {
            val axis = GizmoMath.pickAxis(ray.origin, ray.direction, pivot, axes, length, hitThreshold * size)
            hot = if (axis >= 0) axis
            else if (ray.distanceToPoint(pivot) < boxSize * size) 3 else -1
            if (hot >= 0 && inputProvider.isLeftMouseButtonDown(true)) {
                val param = if (hot < 3) GizmoMath.closestAxisParam(ray.origin, ray.direction, pivot, axes[hot]) else 0f
                if (param != null) {
                    active = hot
                    dragPivot = Vector3f(pivot)
                    dragLength = length
                    dragStartParam = param
                    if (hot < 3) dragAxisDir = Vector3f(axes[hot])
                    uniformFactor = 1f
                    inUse = true
                    beginSnapshot(targets)
                }
            }
        }
        if (active >= 0) {
            if (inputProvider.isLeftMouseButtonDown(true)) drag(ray) else {
                commitSnapshot()
                active = -1
            }
        }

        for (i in 0..2) drawHandle(pivot, axes[i], if (i == active || i == hot) highlight else axisColors[i], length, boxSize * size)
        drawCube(pivot, boxSize * size, if (active == 3 || hot == 3) highlight else Vector3f(1f, 1f, 1f))
    }

    private fun drag(ray: com.pafoid.skate.engine.utils.Ray) {
        val factors = Vector3f(1f, 1f, 1f)
        if (active < 3) {
            val t = GizmoMath.closestAxisParam(ray.origin, ray.direction, dragPivot, dragAxisDir) ?: return
            val f = GizmoMath.snap(1f + (t - dragStartParam) / dragLength, snapStep)
            when (active) { 0 -> factors.x = f; 1 -> factors.y = f; else -> factors.z = f }
        } else {
            uniformFactor *= 1f - inputProvider.getMouseDy() * 0.01f
            val f = GizmoMath.snap(uniformFactor, snapStep)
            factors.set(f, f, f)
        }
        snapshotTargets.forEach { (go, start) ->
            val t = go.getComponent<Transform>() ?: return@forEach
            t.scale.set(
                (start.scale.x * factors.x).coerceAtLeast(MIN_SCALE),
                (start.scale.y * factors.y).coerceAtLeast(MIN_SCALE),
                (start.scale.z * factors.z).coerceAtLeast(MIN_SCALE),
            )
            if (snapshotTargets.size > 1) {
                val axisFactor = if (active < 3) factors[active] else factors.x
                val d = Vector3f(start.translation).sub(dragPivot)
                if (active < 3) {
                    val along = d.dot(dragAxisDir)
                    d.add(Vector3f(dragAxisDir).mul(along * (axisFactor - 1f)))
                } else d.mul(axisFactor)
                t.translation.set(dragPivot).add(d)
            }
        }
    }

    private fun drawHandle(origin: Vector3f, direction: Vector3f, color: Vector3f, length: Float, size: Float) {
        val end = Vector3f(origin).add(Vector3f(direction).mul(length))
        debugRenderer.addLine3D(origin, end, color)
        drawCube(end, size, color)
    }

    private fun drawCube(center: Vector3f, size: Float, color: Vector3f) {
        val h = size / 2f
        val v1 = Vector3f(center).add(-h, -h, -h)
        val v2 = Vector3f(center).add(h, -h, -h)
        val v3 = Vector3f(center).add(h, h, -h)
        val v4 = Vector3f(center).add(-h, h, -h)
        val v5 = Vector3f(center).add(-h, -h, h)
        val v6 = Vector3f(center).add(h, -h, h)
        val v7 = Vector3f(center).add(h, h, h)
        val v8 = Vector3f(center).add(-h, h, h)
        debugRenderer.addTriangle3D(v5, v6, v7, color); debugRenderer.addTriangle3D(v5, v7, v8, color)
        debugRenderer.addTriangle3D(v1, v3, v2, color); debugRenderer.addTriangle3D(v1, v4, v3, color)
        debugRenderer.addTriangle3D(v4, v7, v3, color); debugRenderer.addTriangle3D(v4, v8, v7, color)
        debugRenderer.addTriangle3D(v1, v2, v6, color); debugRenderer.addTriangle3D(v1, v6, v5, color)
        debugRenderer.addTriangle3D(v1, v5, v8, color); debugRenderer.addTriangle3D(v1, v8, v4, color)
        debugRenderer.addTriangle3D(v2, v3, v7, color); debugRenderer.addTriangle3D(v2, v7, v6, color)
    }

    companion object {
        const val MIN_SCALE = 0.01f
    }
}
