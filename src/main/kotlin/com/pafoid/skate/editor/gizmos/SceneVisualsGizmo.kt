package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.Ray
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Draws icons and wire shapes (camera frustum, light range/cone, direction arrows) for every camera and light in the
 * scene and lets the scene view pick them by clicking their icon. Shapes of selected objects are highlighted.
 */
class SceneVisualsGizmo(private val debugRenderer: DebugRenderer) {
    var visible = true

    private val cameraColor = Vector3f(0.85f, 0.85f, 0.9f)
    private val lightColor = Vector3f(1f, 0.9f, 0.3f)
    private val selectedColor = Vector3f(1f, 0.55f, 0f)

    private fun hasCameraOrLight(go: GameObject): Boolean =
        go.getComponent<CameraComponent>() != null || go.getComponent<PointLightComponent>() != null ||
                go.getComponent<SpotLightComponent>() != null || go.getComponent<DirectionalLightComponent>() != null

    private fun visuals(scene: Scene): List<GameObject> {
        val out = ArrayList<GameObject>()
        fun walk(go: GameObject) {
            if (go !== scene && hasCameraOrLight(go)) out.add(go)
            go.children.forEach { walk(it) }
        }
        // Scene-level components (e.g. the default directional light of a scene) live on the scene itself.
        if (hasCameraOrLight(scene)) out.add(scene)
        walk(scene)
        return out
    }

    private fun position(go: GameObject): Vector3f =
        go.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f()) ?: Vector3f()

    /** Icon radius that stays the same on screen: a fraction of the gizmo size for the current camera distance. */
    fun pickRadius(iconSize: Float) = iconSize * 0.9f

    fun draw(scene: Scene, camera: CameraComponent, cameraPosition: Vector3f, iconSizeAt: (Vector3f) -> Float) {
        if (!visible) return
        val right = Vector3f(camera.view.m00(), camera.view.m10(), camera.view.m20())
        val up = Vector3f(camera.view.m01(), camera.view.m11(), camera.view.m21())
        val selected = scene.selectedGameObjects
        for (go in visuals(scene)) {
            val pos = position(go)
            val rotation = go.getComponent<Transform>()?.worldMatrix?.getNormalizedRotation(Quaternionf()) ?: Quaternionf()
            val isSelected = go in selected
            val size = iconSizeAt(pos)
            fun color(base: Vector3f) = if (isSelected) selectedColor else base

            go.getComponent<CameraComponent>()?.let { cam ->
                val icon = EditorVisuals.cameraIcon(pos, right, up, size)
                draw(icon, color(cameraColor))
                if (isSelected || cam.isDefault) {
                    draw(
                        EditorVisuals.cameraFrustum(pos, rotation, cam.isOrthographic, cam.fov, cam.aspectRatio,
                            cam.viewportHeight * cam.zoom, cam.nearPlane, cam.farPlane, maxOf(size * 8f, cam.nearPlane * 2f)),
                        color(cameraColor),
                    )
                }
            }
            go.getComponent<PointLightComponent>()?.let { light ->
                draw(EditorVisuals.lightIcon(pos, right, up, size), color(lightColor))
                if (isSelected) draw(EditorVisuals.rangeSphere(pos, EditorVisuals.pointLightRange(light.constant, light.linear, light.quadratic)), color(lightColor))
            }
            go.getComponent<SpotLightComponent>()?.let { light ->
                draw(EditorVisuals.lightIcon(pos, right, up, size), color(lightColor))
                val range = EditorVisuals.pointLightRange(light.constant, light.linear, light.quadratic)
                draw(EditorVisuals.arrow(pos, light.direction, size * 3f), color(lightColor))
                if (isSelected) draw(EditorVisuals.spotCone(pos, light.direction, light.outerCutOff, range.coerceAtMost(size * 12f)), color(lightColor))
            }
            go.getComponent<DirectionalLightComponent>()?.let { light ->
                draw(EditorVisuals.lightIcon(pos, right, up, size), color(lightColor))
                draw(EditorVisuals.arrow(pos, light.direction, size * 4f), color(lightColor))
            }
        }
    }

    /** The camera/light object whose icon is under [ray], or null. */
    fun pick(scene: Scene, ray: Ray, iconSizeAt: (Vector3f) -> Float): GameObject? {
        if (!visible) return null
        val targets = visuals(scene).mapNotNull { go -> EditorVisuals.Target(go, position(go)) }
        if (targets.isEmpty()) return null
        // Radius depends on distance, so test each target with its own size.
        var best: GameObject? = null
        var bestT = Float.MAX_VALUE
        for (t in targets) {
            val hit = EditorVisuals.pick(ray.origin, ray.direction, listOf(t), pickRadius(iconSizeAt(t.position))) ?: continue
            val dist = ray.origin.distance(t.position)
            if (dist < bestT) { bestT = dist; best = hit }
        }
        return best
    }

    private fun draw(segments: List<EditorVisuals.Segment>, color: Vector3f) =
        segments.forEach { debugRenderer.addLine3D(it.a, it.b, color) }
}
