package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DirectionalLightSystemTest {

    private val stringManager: StringManager = mockk(relaxed = true)

    @Test
    fun updateLightSpaceMatrix_FrustumCalculated_LightSpaceMatrixIsUpdated() {
        // Arrange
        val system = DirectionalLightSystem(stringManager)
        val scene = Scene("TestScene")
        scene.addComponent(DayNightCycleComponent())
        scene.addComponent(DirectionalLightComponent(castShadows = true, autoCalculateBounds = true))
        scene.camera.viewportWidth = 1920
        scene.camera.viewportHeight = 1080
        scene.camera.fov = 45f
        scene.camera.nearPlane = 0.1f
        scene.camera.farPlane = 1000f
        scene.camera.zoom = 1f
        scene.camera.position.set(Vector3f(0f, 10f, 0f))
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
        val system = DirectionalLightSystem(stringManager)
        val scene = Scene("TestScene")
        scene.addComponent(
            DayNightCycleComponent(
                sunDirection = Vector3f(0f, -1f, 0f),
                sunColor = Vector3f(1f, 1f, 1f),
                sunIntensity = 1f
            )
        )
        scene.addComponent(DirectionalLightComponent(castShadows = true, autoCalculateBounds = true))
        scene.camera.viewportWidth = 1920
        scene.camera.viewportHeight = 1080
        scene.camera.fov = 45f
        scene.camera.nearPlane = 0.1f
        scene.camera.farPlane = 1000f
        scene.camera.zoom = 1f
        scene.camera.position.set(Vector3f(0f, 10f, 0f))
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
