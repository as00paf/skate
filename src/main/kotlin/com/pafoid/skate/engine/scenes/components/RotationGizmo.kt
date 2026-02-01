package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.PropertiesWindow
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import kotlin.getValue
import kotlin.math.abs

class RotationGizmo(propertiesWindow: PropertiesWindow) : Gizmo(propertiesWindow), KoinComponent {
    private val debugDraw: DebugDraw by inject()
    private val mouseListener: MouseListener by inject()

    private val radius = 2.0f
    private val hitThreshold = 0.4f
    
    private var xAxisHot = false
    private var yAxisHot = false
    private var zAxisHot = false

    override fun editorUpdate(dt: Float) {
        if (!isInUse()) return

        activeGameObject = propertiesWindow.getActiveObject()
        val go = activeGameObject ?: return
        val pos = go.transform.translation

        val scene = SceneManager.getCurrentScene() ?: return
        val dist = Vector3f(scene.camera.position).distance(pos)
        val dynamicRadius = radius * (dist * 0.1f)
        val dynamicThreshold = hitThreshold * (dist * 0.1f)

        checkInput(dynamicRadius, dynamicThreshold)

        if (xAxisActive) {
            go.transform.rotation.x += mouseListener.getScreenDy()
        } else if (yAxisActive) {
            go.transform.rotation.y += mouseListener.getScreenDx()
        } else if (zAxisActive) {
            go.transform.rotation.z += mouseListener.getScreenDy()
        }

        // Draw Rings
        with(debugDraw) {
            drawCircle(pos, dynamicRadius, Vector3f(1f, 0f, 0f), if (xAxisActive || xAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(1f, 0f, 0f)) // X - Red/Yellow
            drawCircle(pos, dynamicRadius, Vector3f(0f, 1f, 0f), if (yAxisActive || yAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 1f, 0f)) // Y - Green/Yellow
            drawCircle(pos, dynamicRadius, Vector3f(0f, 0f, 1f), if (zAxisActive || zAxisHot) Vector3f(1f, 1f, 0f) else Vector3f(0f, 0f, 1f)) // Z - Blue/Yellow
        }
    }

    override fun isHot(): Boolean = xAxisHot || yAxisHot || zAxisHot

    private fun checkInput(rad: Float, threshold: Float) {
        val scene = SceneManager.getCurrentScene() ?: return
        val go = activeGameObject ?: return
        val pos = go.transform.translation

        val mouseX = mouseListener.getScreenX()
        val mouseY = mouseListener.getScreenY()
        val ray = scene.camera.screenToRay(mouseX, mouseY, 1920f, 1080f)

        // Reset hover states
        xAxisHot = false
        yAxisHot = false
        zAxisHot = false

        if (rayToCircleDist(ray, pos, Vector3f(1f, 0f, 0f), rad) < threshold) xAxisHot = true
        else if (rayToCircleDist(ray, pos, Vector3f(0f, 1f, 0f), rad) < threshold) yAxisHot = true
        else if (rayToCircleDist(ray, pos, Vector3f(0f, 0f, 1f), rad) < threshold) zAxisHot = true

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

    private fun rayToCircleDist(ray: com.pafoid.skate.engine.utils.Ray, center: Vector3f, axis: Vector3f, rad: Float): Float {
        // Plane intersection
        val denom = axis.dot(ray.direction)
        if (abs(denom) < 0.0001f) return Float.MAX_VALUE

        val t = Vector3f(center).sub(ray.origin).dot(axis) / denom
        if (t < 0) return Float.MAX_VALUE

        val hitPoint = Vector3f(ray.origin).add(Vector3f(ray.direction).mul(t))
        val distToCenter = hitPoint.distance(center)
        
        return abs(distToCenter - rad)
    }
}
