package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.editor.PropertiesWindow
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector3f
import org.joml.Vector4f

class TranslateGizmo(propertiesWindow: PropertiesWindow): Gizmo(propertiesWindow) {

    override fun editorUpdate(dt: Float) {
        val go = activeGameObject
        if(go != null) {
            if (xAxisActive) {
                go.transform.translation.x += calculateDelta(Vector3f(1f, 0f, 0f))
            } else if (yAxisActive) {
                go.transform.translation.y += calculateDelta(Vector3f(0f, 1f, 0f))
            } else if (zAxisActive) {
                go.transform.translation.z += calculateDelta(Vector3f(0f, 0f, 1f))
            }
        }

        super.editorUpdate(dt)
    }

    private fun calculateDelta(axis: Vector3f): Float {
        val scene = SceneManager.getCurrentScene() ?: return 0f
        val camera = scene.camera
        val view = camera.createViewMatrix()
        val proj = camera.createProjectionMatrix()
        
        // Project axis direction to screen space
        // We take two points on the axis: origin and origin + axis
        // Convert both to screen space and find the 2D vector
        
        val go = activeGameObject ?: return 0f
        val origin = Vector3f(go.transform.translation)
        val p2 = Vector3f(origin).add(axis)
        
        val s1 = worldToScreen(origin, view, proj, 1920f, 1080f)
        val s2 = worldToScreen(p2, view, proj, 1920f, 1080f)
        
        val axisScreenDir = s2.sub(s1).normalize()
        val mouseDelta = org.joml.Vector2f(MouseListener.getDx(), -MouseListener.getDy()) // Y is inverted in screen/mouse
        
        val projection = mouseDelta.dot(axisScreenDir)
        
        // Scale factor: how many units per pixel?
        // Depends on distance to camera (perspective)
        val dist = Vector3f(camera.position).distance(origin)
        val sensitivity = 0.001f * dist // rough approximation
        
        return projection * sensitivity
    }
    
    private fun worldToScreen(worldPos: Vector3f, view: org.joml.Matrix4f, proj: org.joml.Matrix4f, width: Float, height: Float): org.joml.Vector2f {
        val coords = Vector4f(worldPos, 1.0f)
        view.transform(coords)
        proj.transform(coords)
        
        if (coords.w == 0f) return org.joml.Vector2f()
        
        // NDC
        coords.x /= coords.w
        coords.y /= coords.w
        
        // Screen
        val x = (coords.x + 1) * width / 2f
        val y = (1 - coords.y) * height / 2f // Top-left origin for mouse
        
        return org.joml.Vector2f(x, y)
    }
}