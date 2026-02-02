package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.PropertiesWindow
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import kotlin.getValue
import kotlin.math.abs
import kotlin.math.roundToInt

class TranslateGizmo(sceneManager: SceneManager): Gizmo(sceneManager), KoinComponent {
    private val debugDraw: DebugDraw by inject()
    private val mouseListener: MouseListener by inject()

    private val arrowLength = 2.0f
    private val coneSize = 0.3f
    private val hitThreshold = 0.3f
    
    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false

    override fun init(gameObject: com.pafoid.skate.engine.scenes.GameObject) {
        this.gameObject = gameObject
    }

    override fun start() {}

    override fun update(dt: Float) {}

    override fun editorUpdate(dt: Float) {
        super.editorUpdate(dt)
        activeGameObject?.let{ go ->
            val pos = go.transform.translation

            val scene = sceneManager.currentScene ?: return
            val dist = Vector3f(scene.camera.position).distance(pos)
            val dynamicArrowLength = arrowLength * (dist * 0.1f)
            val dynamicConeSize = coneSize * (dist * 0.1f)
            val dynamicHitThreshold = hitThreshold * (dist * 0.1f)

            // 1. Logic for dragging
            checkInput(dynamicArrowLength, dynamicHitThreshold)

            if (xAxisActive) {
                go.transform.translation.x += calculateDelta(Vector3f(1f, 0f, 0f))
                if (go.getComponent<ModularTile>() != null) go.transform.translation.x = (go.transform.translation.x / 2.0f).roundToInt() * 2.0f
            } else if (yAxisActive) {
                go.transform.translation.y += calculateDelta(Vector3f(0f, 1f, 0f))
                if (go.getComponent<ModularTile>() != null) go.transform.translation.y = (go.transform.translation.y / 2.0f).roundToInt() * 2.0f
            } else if (zAxisActive) {
                go.transform.translation.z += calculateDelta(Vector3f(0f, 0f, 1f))
                if (go.getComponent<ModularTile>() != null) go.transform.translation.z = (go.transform.translation.z / 2.0f).roundToInt() * 2.0f
            }

            // 2. Draw Arrows
            drawArrow(pos, Vector3f(1f, 0f, 0f), if (xAxisActive || xAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(1f, 0f, 0f), dynamicArrowLength, dynamicConeSize) // X - Red/Yellow
            drawArrow(pos, Vector3f(0f, 1f, 0f), if (yAxisActive || yAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 1f, 0f), dynamicArrowLength, dynamicConeSize) // Y - Green/Yellow
            drawArrow(pos, Vector3f(0f, 0f, 1f), if (zAxisActive || zAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 0f, 1f), dynamicArrowLength, dynamicConeSize) // Z - Blue/Yellow
        }
    }

    private fun drawArrow(origin: Vector3f, direction: Vector3f, color: Vector3f, length: Float, cSize: Float) {
        val end = Vector3f(origin).add(Vector3f(direction).mul(length))
        debugDraw.addLine3D(origin, end, color)
        
        // Solid Pyramid at the end
        val ortho1 = if (abs(direction.x) > 0.9f) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)
        val ortho2 = Vector3f(direction).cross(ortho1).normalize().mul(cSize)
        val ortho3 = Vector3f(direction).cross(ortho2).normalize().mul(cSize)
        
        val base = Vector3f(end).sub(Vector3f(direction).mul(cSize))
        val p1 = Vector3f(base).add(ortho2)
        val p2 = Vector3f(base).sub(ortho2)
        val p3 = Vector3f(base).add(ortho3)
        val p4 = Vector3f(base).sub(ortho3)
        
        // Draw 4 triangles for the sides
        debugDraw.addTriangle3D(end, p1, p3, color)
        debugDraw.addTriangle3D(end, p3, p2, color)
        debugDraw.addTriangle3D(end, p2, p4, color)
        debugDraw.addTriangle3D(end, p4, p1, color)
        
        // Base triangles
        debugDraw.addTriangle3D(p1, p2, p3, color)
        debugDraw.addTriangle3D(p1, p2, p4, color)
    }

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

                if (xAxisActive || yAxisActive || zAxisActive) {
                    oldTransform = Transform().apply { copyFrom(go.transform) }
                }
            }
        } else {
            if (xAxisActive || yAxisActive || zAxisActive) {
                // We were dragging and just stopped
                oldTransform?.let { old ->
                    if (old != go.transform) {
                        undoRedoManager.pushCommand(com.pafoid.skate.engine.editor.TransformCommand(go, old, go.transform))
                    }
                }
                oldTransform = null
            }
            xAxisActive = false
            yAxisActive = false
            zAxisActive = false
        }
    }
    
    override fun isHot(): Boolean = xAxisHot || yAxisHot || zAxisHot

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
        val mouseDelta = Vector2f(mouseListener.getScreenDx(), mouseListener.getScreenDy())
        
        val projection = mouseDelta.dot(axisScreenDir)
        val dist = Vector3f(camera.position).distance(origin)
        val sensitivity = 0.01f * dist
        
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