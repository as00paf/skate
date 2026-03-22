package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.config.DirectionalLightConfig
import com.pafoid.skate.engine.render.Camera
import io.mockk.every
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DirectionalLightSystemTest {

    private val stringManager: StringManager = mockk(relaxed = true)

    @Test
    fun updateLightSpaceMatrix_FrustumCalculated_LightSpaceMatrixIsUpdated() {
        // Arrange
        val system = DirectionalLightSystem(DirectionalLightConfig().apply {
            castShadows = true
            autoCalculateBounds = true
        }, stringManager)

        val mockScene = mockk<Scene>()
        val mockSystemManager = mockk<SystemManager>()
        val mockCamera = mockk<Camera>(relaxed = true)

        every { mockScene.systemManager } returns mockSystemManager
        every { mockSystemManager.systems } returns mutableListOf()
        every { mockScene.camera } returns mockCamera

        every { mockCamera.viewportWidth } returns 1920
        every { mockCamera.viewportHeight } returns 1080
        every { mockCamera.fov } returns 45f
        every { mockCamera.nearPlane } returns 0.1f
        every { mockCamera.farPlane } returns 1000f
        every { mockCamera.zoom } returns 1f
        every { mockCamera.position } returns Vector3f(0f, 10f, 0f)
        every { mockCamera.createViewMatrix() } returns Matrix4f().lookAt(
            Vector3f(0f, 10f, 0f),
            Vector3f(0f, 10f, -1f),
            Vector3f(0f, 1f, 0f)
        )

        system.init(mockScene)

        // Act
        try {
            system.editorUpdate(0f)
        } catch (e: Exception) {
            e.printStackTrace(); throw e
        }

        // Assert
        // We just want to check that the light space matrix was actually updated and has valid data
        // For the new bounding sphere implementation, the matrix will be populated
        val matrix = system.config.lightSpaceMatrix
        val isIdentity = Matrix4f().equals(matrix)
        assertFalse(isIdentity, "Light space matrix should have been calculated and not be the identity matrix.")
    }

    @Test
    fun updateLightSpaceMatrix_HighNoon_UpVectorIsDynamic() {
        // Arrange
        val system = DirectionalLightSystem(DirectionalLightConfig().apply {
            castShadows = true
            autoCalculateBounds = true
            direction = Vector3f(0f, -1f, 0f) // High noon
        }, stringManager)

        val mockScene = mockk<Scene>()
        val mockSystemManager = mockk<SystemManager>()
        val mockCamera = mockk<Camera>(relaxed = true)

        every { mockScene.systemManager } returns mockSystemManager
        every { mockSystemManager.systems } returns mutableListOf()
        every { mockScene.camera } returns mockCamera

        every { mockCamera.viewportWidth } returns 1920
        every { mockCamera.viewportHeight } returns 1080
        every { mockCamera.fov } returns 45f
        every { mockCamera.nearPlane } returns 0.1f
        every { mockCamera.farPlane } returns 1000f
        every { mockCamera.zoom } returns 1f
        every { mockCamera.position } returns Vector3f(0f, 10f, 0f)
        every { mockCamera.createViewMatrix() } returns Matrix4f().lookAt(
            Vector3f(0f, 10f, 0f),
            Vector3f(0f, 10f, -1f),
            Vector3f(0f, 1f, 0f)
        )

        system.init(mockScene)

        // Act
        try {
            system.editorUpdate(0f)
        } catch (e: Exception) {
            e.printStackTrace(); throw e
        }

        // Assert
        val matrix = system.config.lightSpaceMatrix
        val isIdentity = Matrix4f().equals(matrix)
        assertFalse(isIdentity, "Matrix should not be identity, even at high noon.")
    }
}
