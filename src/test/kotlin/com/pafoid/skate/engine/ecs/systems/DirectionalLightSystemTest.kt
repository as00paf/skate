package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DirectionalLightSystemTest {

    private val cameraManager = CameraManager(EventSystem())

    @Test
    fun updateLightSpaceMatrix_FrustumCalculated_LightSpaceMatrixIsUpdated() {
        // Arrange
        val system = DirectionalLightSystem(cameraManager)
        val scene = Scene("TestScene")
        scene.addComponent(DayNightCycleComponent())
        scene.addComponent(DirectionalLightComponent(castShadows = true, autoCalculateBounds = true))
        cameraManager.camera.viewportWidth = 1920
        cameraManager.camera.viewportHeight = 1080
        cameraManager.camera.fov = 45f
        cameraManager.camera.nearPlane = 0.1f
        cameraManager.camera.farPlane = 1000f
        cameraManager.camera.zoom = 1f
        cameraManager.transform.translation.set(Vector3f(0f, 10f, 0f))
        system.init(scene)

        // Act
        system.update(0f)

        // Assert
        val matrix = system.config?.lightSpaceMatrix
        assertNotNull(matrix)
        val isIdentity = Matrix4f().equals(matrix)
        assertFalse(isIdentity, "Light space matrix should have been calculated and not be the identity matrix.")
    }

    @Test
    fun updateLightSpaceMatrix_HighNoon_UpVectorIsDynamic() {
        // Arrange
        val system = DirectionalLightSystem(cameraManager)
        val scene = Scene("TestScene")
        scene.addComponent(
            DayNightCycleComponent(timeOfDay = 12f)
        )
        scene.addComponent(DirectionalLightComponent(direction = Vector3f(0f, -1f, 0f), castShadows = true, autoCalculateBounds = true))
        cameraManager.camera.viewportWidth = 1920
        cameraManager.camera.viewportHeight = 1080
        cameraManager.camera.fov = 45f
        cameraManager.camera.nearPlane = 0.1f
        cameraManager.camera.farPlane = 1000f
        cameraManager.camera.zoom = 1f
        cameraManager.transform.translation.set(Vector3f(0f, 10f, 0f))
        system.init(scene)

        // Act
        system.update(0f)

        // Assert
        val matrix = system.config?.lightSpaceMatrix
        assertNotNull(matrix)
        val isIdentity = Matrix4f().equals(matrix)
        assertFalse(isIdentity, "Matrix should not be identity, even at high noon.")
    }
}
