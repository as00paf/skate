package com.pafoid.skate.engine.ecs.components

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for PhysicsComponent.
 *
 * Tests cover:
 * - Default property values
 * - updateFromPhysics() functionality
 * - Derived state computation (speed, isMoving, isRotating)
 * - reset() functionality
 */
class PhysicsComponentTest {

    // Tolerance for floating point comparisons
    private val epsilon = 0.0001f

    // =========================================================================
    // DEFAULT VALUES TESTS
    // =========================================================================

    @Test
    fun `PhysicsComponent default values are correct`() {
        // Arrange & Act
        val component = PhysicsComponent()

        // Assert - Velocity defaults to zero
        assertEquals(0.0f, component.linearVelocity.x, epsilon)
        assertEquals(0.0f, component.linearVelocity.y, epsilon)
        assertEquals(0.0f, component.linearVelocity.z, epsilon)

        assertEquals(0.0f, component.angularVelocity.x, epsilon)
        assertEquals(0.0f, component.angularVelocity.y, epsilon)
        assertEquals(0.0f, component.angularVelocity.z, epsilon)

        // Assert - Derived state defaults
        assertEquals(0.0f, component.speed, epsilon)
        assertFalse(component.isMoving, "Should not be moving by default")
        assertFalse(component.isRotating, "Should not be rotating by default")
    }

    // =========================================================================
    // UPDATE FROM PHYSICS TESTS
    // =========================================================================

    @Test
    fun `updateFromPhysics updates velocity properties`() {
        // Arrange
        val component = PhysicsComponent()
        val linearVel = Vector3f(1.0f, 2.0f, 3.0f)
        val angularVel = Vector3f(0.1f, 0.2f, 0.3f)

        // Act
        component.updateFromPhysics(linearVel, angularVel)

        // Assert
        assertEquals(1.0f, component.linearVelocity.x, epsilon)
        assertEquals(2.0f, component.linearVelocity.y, epsilon)
        assertEquals(3.0f, component.linearVelocity.z, epsilon)

        assertEquals(0.1f, component.angularVelocity.x, epsilon)
        assertEquals(0.2f, component.angularVelocity.y, epsilon)
        assertEquals(0.3f, component.angularVelocity.z, epsilon)
    }

    @Test
    fun `updateFromPhysics computes correct speed magnitude`() {
        // Arrange
        val component = PhysicsComponent()
        // Speed = sqrt(3^2 + 4^2) = 5
        val linearVel = Vector3f(3.0f, 4.0f, 0.0f)

        // Act
        component.updateFromPhysics(linearVel, Vector3f())

        // Assert
        assertEquals(5.0f, component.speed, epsilon)
    }

    @Test
    fun `updateFromPhysics sets isMoving when speed exceeds threshold`() {
        // Arrange
        val component = PhysicsComponent()
        val movingVel = Vector3f(1.0f, 0.0f, 0.0f) // speed = 1.0 > 0.01

        // Act
        component.updateFromPhysics(movingVel, Vector3f())

        // Assert
        assertTrue(component.isMoving, "Should be moving when speed > 0.01")
    }

    @Test
    fun `updateFromPhysics clears isMoving when speed below threshold`() {
        // Arrange
        val component = PhysicsComponent()
        val stationaryVel = Vector3f(0.001f, 0.0f, 0.0f) // speed = 0.001 < 0.01

        // Act
        component.updateFromPhysics(stationaryVel, Vector3f())

        // Assert
        assertFalse(component.isMoving, "Should not be moving when speed < 0.01")
    }

    @Test
    fun `updateFromPhysics sets isRotating when angular velocity exceeds threshold`() {
        // Arrange
        val component = PhysicsComponent()
        val rotatingVel = Vector3f(0.1f, 0.0f, 0.0f) // angular speed = 0.1 > 0.01

        // Act
        component.updateFromPhysics(Vector3f(), rotatingVel)

        // Assert
        assertTrue(component.isRotating, "Should be rotating when angular speed > 0.01")
    }

    @Test
    fun `updateFromPhysics clears isRotating when angular velocity below threshold`() {
        // Arrange
        val component = PhysicsComponent()
        val nonRotatingVel = Vector3f(0.001f, 0.0f, 0.0f) // angular speed = 0.001 < 0.01

        // Act
        component.updateFromPhysics(Vector3f(), nonRotatingVel)

        // Assert
        assertFalse(component.isRotating, "Should not be rotating when angular speed < 0.01")
    }

    // =========================================================================
    // RESET FUNCTIONALITY TESTS
    // =========================================================================

    @Test
    fun `reset restores all properties to default values`() {
        // Arrange - modify all properties
        val component = PhysicsComponent().apply {
            linearVelocity.set(10.0f, 20.0f, 30.0f)
            angularVelocity.set(1.0f, 2.0f, 3.0f)
            // Force update derived state
            updateFromPhysics(linearVelocity, angularVelocity)
        }

        // Act
        component.reset()

        // Assert - Velocity restored to zero
        assertEquals(0.0f, component.linearVelocity.x, epsilon)
        assertEquals(0.0f, component.linearVelocity.y, epsilon)
        assertEquals(0.0f, component.linearVelocity.z, epsilon)

        assertEquals(0.0f, component.angularVelocity.x, epsilon)
        assertEquals(0.0f, component.angularVelocity.y, epsilon)
        assertEquals(0.0f, component.angularVelocity.z, epsilon)

        // Assert - Derived state restored
        assertEquals(0.0f, component.speed, epsilon)
        assertFalse(component.isMoving)
        assertFalse(component.isRotating)
    }

    // =========================================================================
    // EDGE CASE TESTS
    // =========================================================================

    @Test
    fun `updateFromPhysics handles zero velocity correctly`() {
        // Arrange
        val component = PhysicsComponent()

        // Act
        component.updateFromPhysics(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f))

        // Assert
        assertEquals(0.0f, component.speed, epsilon)
        assertFalse(component.isMoving)
        assertFalse(component.isRotating)
    }

    @Test
    fun `updateFromPhysics handles negative velocity correctly`() {
        // Arrange
        val component = PhysicsComponent()
        val negativeVel = Vector3f(-5.0f, -3.0f, -1.0f)

        // Act
        component.updateFromPhysics(negativeVel, Vector3f())

        // Assert - Speed should be positive magnitude
        val expectedSpeed = kotlin.math.sqrt(5.0f * 5.0f + 3.0f * 3.0f + 1.0f * 1.0f)
        assertEquals(expectedSpeed, component.speed, epsilon)
        assertTrue(component.isMoving)
    }

    @Test
    fun `updateFromPhysics handles diagonal rotation correctly`() {
        // Arrange
        val component = PhysicsComponent()
        val diagonalRot = Vector3f(1.0f, 1.0f, 1.0f)

        // Act
        component.updateFromPhysics(Vector3f(), diagonalRot)

        // Assert
        val expectedAngularSpeed = kotlin.math.sqrt(3.0f) // sqrt(1^2 + 1^2 + 1^2)
        assertEquals(expectedAngularSpeed, component.angularVelocity.length(), epsilon)
        assertTrue(component.isRotating)
    }
}
