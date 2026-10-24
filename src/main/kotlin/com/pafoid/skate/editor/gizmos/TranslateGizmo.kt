package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.Ray
import org.joml.Vector3f
import kotlin.math.abs

class TranslateGizmo(
    engine: Engine,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(engine, undoRedoManager) {
    private val arrowLength = 1.0f
    private val coneSize = 0.12f
    private val hitThreshold = 0.1f

    /** 0..2 axis handles, 3..5 plane handles (plane k-3 is perpendicular to that axis). */
    private var hot = -1
    private var active = -1
    private var dragAxisDir = Vector3f()
    private var dragPlaneNormal = Vector3f()
    private var dragPlaneAxes = Pair(Vector3f(), Vector3f())
    private var dragStartPivot = Vector3f()
    private var dragStartParam = 0f
    private var dragStartHit = Vector3f()

    var localSpace = false
    var snapStep = 0f

    private val axisColors = arrayOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))
    private val highlight = Vector3f(1f, 1f, 0f)

    fun update(targets: List<GameObject>, camera: CameraComponent, cameraPosition: Vector3f) {
        val transforms = targets.mapNotNull { it.getComponent<Transform>() }
        if (transforms.isEmpty()) return
        val pivot = GizmoMath.pivot(transforms.map { it.translation })
        val fov = Math.toRadians(camera.fov.toDouble()).toFloat()
        val size = GizmoMath.screenSpaceScale(cameraPosition.distance(pivot), fov)
        val length = arrowLength * size
        val axes = GizmoMath.axes(transforms.first().rotation, localSpace)
        val ray = inputProvider.screenToRay(camera)

        if (active < 0) {
            hot = pick(ray, pivot, axes, size, length)
            if (hot >= 0 && inputProvider.isLeftMouseButtonDown(true)) beginDrag(hot, axes, pivot, ray, targets)
        }
        if (active >= 0) {
            if (inputProvider.isLeftMouseButtonDown(true)) drag(ray) else endDrag()
        }

        val drawPivot = pivot
        for (i in 0..2) {
            val color = if (i == active || i == hot) highlight else axisColors[i]
            drawArrow(drawPivot, axes[i], color, length, coneSize * size)
            drawPlaneHandle(drawPivot, axes, i, size, if (i + 3 == active || i + 3 == hot) highlight else axisColors[i])
        }
    }

    private fun pick(ray: Ray, pivot: Vector3f, axes: Array<Vector3f>, size: Float, length: Float): Int {
        val plane = GizmoMath.pickPlane(ray.origin, ray.direction, pivot, axes, size)
        val axis = GizmoMath.pickAxis(ray.origin, ray.direction, pivot, axes, length, hitThreshold * size)
        return if (axis >= 0) axis else if (plane >= 0) plane + 3 else -1
    }

    private fun beginDrag(handle: Int, axes: Array<Vector3f>, pivot: Vector3f, ray: Ray, targets: List<GameObject>) {
        dragStartPivot = Vector3f(pivot)
        if (handle < 3) {
            dragAxisDir = Vector3f(axes[handle])
            dragStartParam = GizmoMath.closestAxisParam(ray.origin, ray.direction, pivot, dragAxisDir) ?: return
        } else {
            val i = handle - 3
            dragPlaneNormal = Vector3f(axes[i])
            dragPlaneAxes = Pair(Vector3f(axes[(i + 1) % 3]), Vector3f(axes[(i + 2) % 3]))
            dragStartHit = GizmoMath.rayPlaneHit(ray.origin, ray.direction, pivot, dragPlaneNormal) ?: return
        }
        active = handle
        inUse = true
        beginSnapshot(targets)
    }

    private fun drag(ray: Ray) {
        val delta = Vector3f()
        if (active < 3) {
            val t = GizmoMath.closestAxisParam(ray.origin, ray.direction, dragStartPivot, dragAxisDir) ?: return
            delta.set(dragAxisDir).mul(GizmoMath.snap(t - dragStartParam, snapStep))
        } else {
            val hit = GizmoMath.rayPlaneHit(ray.origin, ray.direction, dragStartPivot, dragPlaneNormal) ?: return
            val d = Vector3f(hit).sub(dragStartHit)
            val u = GizmoMath.snap(d.dot(dragPlaneAxes.first), snapStep)
            val v = GizmoMath.snap(d.dot(dragPlaneAxes.second), snapStep)
            delta.set(Vector3f(dragPlaneAxes.first).mul(u)).add(Vector3f(dragPlaneAxes.second).mul(v))
        }
        snapshotTargets.forEach { (go, start) ->
            go.getComponent<Transform>()?.translation?.set(start.translation)?.add(delta)
        }
    }

    private fun endDrag() {
        commitSnapshot()
        active = -1
    }

    private fun drawPlaneHandle(origin: Vector3f, axes: Array<Vector3f>, i: Int, size: Float, color: Vector3f) {
        val a = axes[(i + 1) % 3]
        val b = axes[(i + 2) % 3]
        fun p(u: Float, v: Float) = Vector3f(origin).add(Vector3f(a).mul(u * size)).add(Vector3f(b).mul(v * size))
        val lo = GizmoMath.PLANE_MIN
        val hi = GizmoMath.PLANE_MAX
        val c = arrayOf(p(lo, lo), p(hi, lo), p(hi, hi), p(lo, hi))
        for (k in 0..3) debugRenderer.addLine3D(c[k], c[(k + 1) % 4], color)
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
}
