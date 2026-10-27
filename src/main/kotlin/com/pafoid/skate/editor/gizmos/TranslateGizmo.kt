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
import kotlin.math.cos
import kotlin.math.sin

class TranslateGizmo(
    engine: Engine,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(engine, undoRedoManager) {
    private val arrowLength = 1.0f
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
        val pivot = GizmoMath.pivot(transforms.map { it.worldMatrix.getTranslation(Vector3f()) })
        val size = GizmoMath.gizmoSize(
            camera.isOrthographic, cameraPosition.distance(pivot), camera.fov, camera.zoom, camera.viewportHeight,
        )
        val length = arrowLength * size
        val ray = inputProvider.screenToRay(camera)

        if (active < 0) {
            displayAxes = GizmoMath.facingViewer(baseAxes(transforms.first()), viewDirection(camera, cameraPosition, pivot))
            hot = pick(ray, pivot, displayAxes, size, length)
            if (hot >= 0 && inputProvider.isLeftMouseButtonDown(true)) beginDrag(hot, displayAxes, pivot, ray, targets)
        }
        if (active >= 0) {
            if (inputProvider.isLeftMouseButtonDown(true)) drag(ray) else endDrag()
        }

        val axes = displayAxes
        for (i in 0..2) {
            val color = if (i == active || i == hot) highlight else axisColors[i]
            drawArrow(pivot, axes[i], color, length, size)
            val planeHot = i + 3 == active || i + 3 == hot
            drawPlaneHandle(pivot, axes, i, size, if (planeHot) highlight else axisColors[i], planeHot)
        }
    }

    private var displayAxes = GizmoMath.axes(Vector3f(), false)

    private fun baseAxes(t: Transform): Array<Vector3f> =
        if (!localSpace) GizmoMath.axes(Vector3f(), false)
        else arrayOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))
            .map { t.worldMatrix.transformDirection(it).normalize() }.toTypedArray()

    private fun viewDirection(camera: CameraComponent, cameraPosition: Vector3f, pivot: Vector3f): Vector3f =
        if (camera.isOrthographic) Vector3f(-camera.view.m02(), -camera.view.m12(), -camera.view.m22())
        else Vector3f(pivot).sub(cameraPosition).normalize()

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
            val local = GizmoMath.toParentSpace(delta, go.parent?.getComponent<Transform>()?.worldMatrix)
            go.getComponent<Transform>()?.translation?.set(start.translation)?.add(local)
        }
    }

    private fun endDrag() {
        commitSnapshot()
        active = -1
    }

    private fun drawPlaneHandle(
        origin: Vector3f, axes: Array<Vector3f>, i: Int, size: Float, color: Vector3f, filled: Boolean,
    ) {
        val a = axes[(i + 1) % 3]
        val b = axes[(i + 2) % 3]
        fun p(u: Float, v: Float) = Vector3f(origin).add(Vector3f(a).mul(u * size)).add(Vector3f(b).mul(v * size))
        val lo = GizmoMath.PLANE_MIN
        val hi = GizmoMath.PLANE_MAX
        val c = arrayOf(p(lo, lo), p(hi, lo), p(hi, hi), p(lo, hi))
        val fill = if (filled) color else Vector3f(color).mul(0.55f)
        debugRenderer.addTriangle3D(c[0], c[1], c[2], fill)
        debugRenderer.addTriangle3D(c[0], c[2], c[3], fill)
        debugRenderer.addTriangle3D(c[0], c[2], c[1], fill)
        debugRenderer.addTriangle3D(c[0], c[3], c[2], fill)
        for (k in 0..3) debugRenderer.addLine3D(c[k], c[(k + 1) % 4], color)
    }

    /** Shaft up to a slim, pointed cone whose base is [CONE_LENGTH] short of the tip. */
    private fun drawArrow(origin: Vector3f, direction: Vector3f, color: Vector3f, length: Float, size: Float) {
        val tip = Vector3f(origin).add(Vector3f(direction).mul(length))
        val coneLength = CONE_LENGTH * size
        val radius = CONE_RADIUS * size
        val base = Vector3f(tip).sub(Vector3f(direction).mul(coneLength))
        debugRenderer.addLine3D(origin, base, color)

        val helper = if (abs(direction.y) > 0.9f) Vector3f(1f, 0f, 0f) else Vector3f(0f, 1f, 0f)
        val u = Vector3f(direction).cross(helper).normalize().mul(radius)
        val v = Vector3f(direction).cross(u).normalize().mul(radius)
        val ring = Array(CONE_SEGMENTS) { k ->
            val ang = 2.0 * Math.PI * k / CONE_SEGMENTS
            Vector3f(base).add(Vector3f(u).mul(cos(ang).toFloat())).add(Vector3f(v).mul(sin(ang).toFloat()))
        }
        for (k in ring.indices) {
            val next = ring[(k + 1) % ring.size]
            debugRenderer.addTriangle3D(tip, ring[k], next, color)
            debugRenderer.addTriangle3D(base, next, ring[k], color)
            debugRenderer.addTriangle3D(tip, next, ring[k], color)
            debugRenderer.addTriangle3D(base, ring[k], next, color)
        }
    }

    private companion object {
        const val CONE_LENGTH = 0.2f
        const val CONE_RADIUS = 0.06f
        const val CONE_SEGMENTS = 10
    }
}
