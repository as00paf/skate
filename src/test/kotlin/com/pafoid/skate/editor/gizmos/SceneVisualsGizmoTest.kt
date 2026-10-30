package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent

import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.Ray
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SceneVisualsGizmoTest {
    private lateinit var debugRenderer: DebugRenderer
    private lateinit var gizmo: SceneVisualsGizmo
    private val camera = CameraComponent().apply {
        fov = 45f
        viewportHeight = 1080
        nearPlane = 0.1f
        farPlane = 100f
        zoom = 1f
        isOrthographic = false
    }

    @BeforeEach
    fun setUp() {
        debugRenderer = mockk(relaxed = true)
        gizmo = SceneVisualsGizmo(debugRenderer)
    }

    private fun cameraGo(scene: Scene, name: String, position: Vector3f, isDefault: Boolean = false): GameObject {
        val go = GameObject(name)
        val transform = Transform().apply { translation.set(position) }
        go.addComponent(transform)
        go.addComponent(CameraComponent().apply { this.isDefault = isDefault })
        go.getComponent<Transform>()!!.worldMatrix.identity().translate(position)
        scene.addChild(go)
        return go
    }

    private fun lightGo(scene: Scene, name: String, position: Vector3f, light: Component): GameObject {
        val go = GameObject(name)
        val transform = Transform().apply { translation.set(position) }
        go.addComponent(transform)
        when (light) {
            is PointLightComponent -> go.addComponent(light)
            is SpotLightComponent -> go.addComponent(light)
        }
        go.getComponent<Transform>()!!.worldMatrix.identity().translate(position)
        scene.addChild(go)
        return go
    }

    private fun plainGo(scene: Scene, name: String, position: Vector3f): GameObject {
        val go = GameObject(name)
        go.addComponent(Transform().apply { translation.set(position) })
        scene.addChild(go)
        return go
    }

    @Test
    fun SceneVisuals_DrawCameraIconOnlyWhenNotSelected() {
        val scene = Scene("MainScene")
        cameraGo(scene, "Camera", Vector3f(0f, 5f, 20f))
        scene.clearSelection()

        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })

        // Camera icon only (7 segments): not selected and not the default camera, so no frustum.
        verify(exactly = 7) { debugRenderer.addLine3D(any(), any(), any()) }
    }

    @Test
    fun SceneVisuals_DrawCameraFrustumWhenSelected() {
        val scene = Scene("MainScene")
        val camGo = cameraGo(scene, "Camera", Vector3f(0f, 5f, 20f))
        scene.selectedGameObject = camGo

        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })

        // Camera icon (7) + frustum wireframe (12).
        verify(exactly = 19) { debugRenderer.addLine3D(any(), any(), any()) }
    }

    @Test
    fun SceneVisuals_DrawCameraFrustumForDefaultCameraWithoutSelection() {
        val scene = Scene("MainScene")
        cameraGo(scene, "Camera", Vector3f(0f, 5f, 20f), isDefault = true)
        scene.clearSelection()

        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })

        // Default cameras always show their frustum.
        verify(exactly = 19) { debugRenderer.addLine3D(any(), any(), any()) }
    }

    @Test
    fun SceneVisuals_DrawsPointLightIconAndRangeWhenSelected() {
        val scene = Scene("MainScene")
        val lightGo = lightGo(scene, "Point", Vector3f(0f, 5f, 0f), PointLightComponent())

        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })
        // Light bulb icon: circle (16) + rays (8) = 24 segments, no range while unselected.
        verify(exactly = 24) { debugRenderer.addLine3D(any(), any(), any()) }

        scene.selectedGameObject = lightGo
        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })
        // Selected point light also draws its attenuation range sphere: 3 circles x 32 segments = 96. Cumulative: 24 + 120.
        verify(exactly = 144) { debugRenderer.addLine3D(any(), any(), any()) }
    }

    @Test
    fun SceneVisuals_DrawsSpotLightArrowAndConeWhenSelected() {
        val scene = Scene("MainScene")
        val lightGo = lightGo(scene, "Spot", Vector3f(0f, 5f, 0f), SpotLightComponent())

        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })
        // Light bulb icon (24) + direction arrow (1 + 4 head segments = 5) = 29 while unselected.
        verify(exactly = 29) { debugRenderer.addLine3D(any(), any(), any()) }

        scene.selectedGameObject = lightGo
        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })
        // Selected spot light also draws the cone: circle (24) + rim (4) = 28. Cumulative: 29 + 57.
        verify(exactly = 86) { debugRenderer.addLine3D(any(), any(), any()) }
    }

    @Test
    fun SceneVisuals_DrawsSceneLevelDirectionalLightArrow() {
        val scene = Scene("MainScene")
        scene.addComponent(DirectionalLightComponent())

        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })

        // Scene-level directional light: bulb icon (24) + direction arrow (5) = 29.
        verify(exactly = 29) { debugRenderer.addLine3D(any(), any(), any()) }
    }

    @Test
    fun SceneVisuals_DrawIgnoresGameObjectsWithoutCameraOrLight() {
        val scene = Scene("MainScene")
        plainGo(scene, "Plain", Vector3f(0f, 2f, 0f))

        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })

        verify(exactly = 0) { debugRenderer.addLine3D(any(), any(), any()) }
    }

    @Test
    fun SceneVisuals_PickReturnsCameraAndLightUnderRay() {
        val scene = Scene("MainScene")
        val camGo = cameraGo(scene, "Camera", Vector3f(0f, 5f, 20f))
        val lightGo = lightGo(scene, "Point", Vector3f(0f, 5f, 0f), PointLightComponent())

        assertEquals(camGo, gizmo.pick(scene, Ray(Vector3f(0f, 5f, 21f), Vector3f(0f, 0f, -1f)), { _ -> 10f }))
        assertEquals(lightGo, gizmo.pick(scene, Ray(Vector3f(0f, 5f, 0f), Vector3f(0f, 0f, -1f)), { _ -> 10f }))
        assertNull(gizmo.pick(scene, Ray(Vector3f(50f, 5f, 0f), Vector3f(0f, 0f, -1f)), { _ -> 10f }))
    }

    @Test
    fun SceneVisuals_PickReturnsNearestLightWhenSeveral() {
        val scene = Scene("MainScene")
        val nearLight = lightGo(scene, "PointNear", Vector3f(0f, 5f, 0f), PointLightComponent())
        val farLight = lightGo(scene, "PointFar", Vector3f(0f, 5f, 30f), PointLightComponent())

        assertEquals(nearLight, gizmo.pick(scene, Ray(Vector3f(0f, 5f, 0f), Vector3f(0f, 0f, -1f)), { _ -> 10f }))
    }

    @Test
    fun SceneVisuals_HiddenDrawsNothingAndDoesNotPick() {
        val scene = Scene("MainScene")
        cameraGo(scene, "Camera", Vector3f(0f, 5f, 20f))

        gizmo.visible = false
        gizmo.draw(scene, camera, Vector3f(), { _ -> 10f })
        verify(exactly = 0) { debugRenderer.addLine3D(any(), any(), any()) }
        assertNull(gizmo.pick(scene, Ray(Vector3f(0f, 5f, 21f), Vector3f(0f, 0f, -1f)), { _ -> 10f }))
    }
}
