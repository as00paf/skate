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
    private val arrowLength = 1.0f
    private val coneSize = 0.12f
    private val hitThreshold = 0.1f

    private var hotAxis = -1
    private var activeAxis = -1
    private var dragStartParam = 0f
    private var dragStartPos = Vector3f()
    private var dragAxisDir = Vector3f()

    private var oldTransform: Transform? = null

    var localSpace = false
    var snapStep = 0f

    private val axisColors = arrayOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))
    private val highlight = Vector3f(1f, 1f, 0f)

    fun update(activeGameObject: GameObject?, camera: CameraComponent, cameraPosition: Vector3f) {
        val go = activeGameObject ?: return
        val transform = go.getComponent<Transform>() ?: return
        val pos = transform.translation
        val fov = Math.toRadians(camera.fov.toDouble()).toFloat()
        val size = GizmoMath.screenSpaceScale(cameraPosition.distance(pos), fov)
        val length = arrowLength * size
        val coneLen = coneSize * size
        val axes = GizmoMath.axes(transform.rotation, localSpace)
        val ray = inputProvider.screenToRay(camera)

        if (activeAxis < 0) {
            hotAxis = GizmoMath.pickAxis(ray.origin, ray.direction, pos, axes, length, hitThreshold * size)
            if (hotAxis >= 0 && inputProvider.isLeftMouseButtonDown(true)) beginDrag(hotAxis, axes[hotAxis], transform, ray)
        }

        if (activeAxis >= 0) {
            if (inputProvider.isLeftMouseButtonDown(true)) {
                drag(transform, ray)
            } else {
                endDrag(go, transform)
            }
        }

        for (i in 0..2) {
            val color = if (i == activeAxis || i == hotAxis) highlight else axisColors[i]
            drawArrow(pos, axes[i], color, length, coneLen)
        }
    }

    private fun beginDrag(axis: Int, dir: Vector3f, transform: Transform, ray: com.pafoid.skate.engine.utils.Ray) {
        val t = GizmoMath.closestAxisParam(ray.origin, ray.direction, transform.translation, dir) ?: return
        activeAxis = axis
        dragAxisDir = Vector3f(dir)
        dragStartPos = Vector3f(transform.translation)
        dragStartParam = t
        oldTransform = Transform().apply { copyFrom(transform) }
        inUse = true
    }

    private fun drag(transform: Transform, ray: com.pafoid.skate.engine.utils.Ray) {
        val t = GizmoMath.closestAxisParam(ray.origin, ray.direction, dragStartPos, dragAxisDir) ?: return
        val delta = GizmoMath.snap(t - dragStartParam, snapStep)
        transform.translation.set(dragStartPos).add(Vector3f(dragAxisDir).mul(delta))
    }

    private fun endDrag(go: GameObject, transform: Transform) {
        oldTransform?.let { old ->
            if (old != transform) {
                undoRedoManager.pushCommand(TransformCommand(go, old, transform))
                sceneManager.currentScene?.isDirty = true
            }
        }
        oldTransform = null
        activeAxis = -1
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
