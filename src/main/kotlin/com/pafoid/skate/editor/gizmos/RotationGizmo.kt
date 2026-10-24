package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Quaternionf
import org.joml.Vector3f

class RotationGizmo(
    engine: Engine,
    undoRedoManager: UndoRedoManager,
    private val debugRenderer: DebugRenderer,
) : Gizmo(engine, undoRedoManager) {
    private val radiusFactor = 1.0f
    private val hitThreshold = 0.1f

    private var hot = -1
    private var active = -1
    private var dragAxis = Vector3f()
    private var dragPivot = Vector3f()
    private var dragStartDir = Vector3f()

    var localSpace = false
    /** Rotation snap step in degrees; 0 disables snapping. */
    var snapDegrees = 0f

    private val axisColors = arrayOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))
    private val highlight = Vector3f(1f, 1f, 0f)

    fun update(targets: List<GameObject>, camera: CameraComponent, cameraPosition: Vector3f) {
        val transforms = targets.mapNotNull { it.getComponent<Transform>() }
        if (transforms.isEmpty()) return
        val pivot = if (active >= 0) dragPivot else GizmoMath.pivot(transforms.map { it.translation })
        val fov = Math.toRadians(camera.fov.toDouble()).toFloat()
        val radius = radiusFactor * GizmoMath.screenSpaceScale(cameraPosition.distance(pivot), fov)
        val axes = GizmoMath.axes(transforms.first().rotation, localSpace)
        val ray = inputProvider.screenToRay(camera)

        if (active < 0) {
            hot = GizmoMath.pickRing(ray.origin, ray.direction, pivot, axes, radius, hitThreshold * radius)
            if (hot >= 0 && inputProvider.isLeftMouseButtonDown(true)) {
                val hit = GizmoMath.rayPlaneHit(ray.origin, ray.direction, pivot, axes[hot])
                if (hit != null) {
                    active = hot
                    dragAxis = Vector3f(axes[hot])
                    dragPivot = Vector3f(pivot)
                    dragStartDir = Vector3f(hit).sub(pivot).normalize()
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

        for (i in 0..2) {
            debugRenderer.drawCircle(pivot, radius, axes[i], if (i == active || i == hot) highlight else axisColors[i])
        }
    }

    private fun drag(ray: com.pafoid.skate.engine.utils.Ray) {
        val hit = GizmoMath.rayPlaneHit(ray.origin, ray.direction, dragPivot, dragAxis) ?: return
        val dir = Vector3f(hit).sub(dragPivot).normalize()
        var angle = GizmoMath.signedAngle(dragStartDir, dir, dragAxis)
        if (snapDegrees > 0f) {
            val snapped = GizmoMath.snap(Math.toDegrees(angle.toDouble()).toFloat(), snapDegrees)
            angle = Math.toRadians(snapped.toDouble()).toFloat()
        }
        val q = Quaternionf().rotationAxis(angle, dragAxis)
        snapshotTargets.forEach { (go, start) ->
            val t = go.getComponent<Transform>() ?: return@forEach
            t.rotation.set(GizmoMath.rotateEuler(start.rotation, dragAxis, angle))
            t.translation.set(Vector3f(start.translation).sub(dragPivot).rotate(q).add(dragPivot))
        }
    }
}
