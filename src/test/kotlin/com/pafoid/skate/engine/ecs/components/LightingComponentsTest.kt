package com.pafoid.skate.engine.ecs.components

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for LightingStateComponent and LightingComponent.
 *
 * Tests cover:
 * - Default property values
 * - reset() functionality
 * - Vector3f property isolation
 */
class LightingComponentsTest {

    // Tolerance for floating point comparisons
    private val epsilon = 0.0001f

    // =========================================================================
    // LIGHTINGSTATECOMPONENT TESTS
    // =========================================================================

    @Test
    fun `LightingStateComponent default values are correct`() {
        // Arrange & Act
        val component = LightingStateComponent()

        // Assert
        assertEquals(0.3f, component.ambientLight.x, epsilon)
        assertEquals(0.3f, component.ambientLight.y, epsilon)
        assertEquals(0.35f, component.ambientLight.z, epsilon)
        assertTrue(component.useAmbient, "useAmbient should default to true")
    }

    @Test
    fun `LightingStateComponent custom values are preserved`() {
        // Arrange & Act
        val component = LightingStateComponent(
            ambientLight = Vector3f(0.5f, 0.5f, 0.6f),
            useAmbient = false
        )

        // Assert
        assertEquals(0.5f, component.ambientLight.x, epsilon)
        assertEquals(0.5f, component.ambientLight.y, epsilon)
        assertEquals(0.6f, component.ambientLight.z, epsilon)
        assertEquals(false, component.useAmbient)
    }

    @Test
    fun `LightingStateComponent reset restores defaults`() {
        // Arrange
        val component = LightingStateComponent().apply {
            ambientLight.set(1.0f, 1.0f, 1.0f)
            useAmbient = false
        }

        // Act
        component.reset()

        // Assert
        assertEquals(0.3f, component.ambientLight.x, epsilon)
        assertEquals(0.3f, component.ambientLight.y, epsilon)
        assertEquals(0.35f, component.ambientLight.z, epsilon)
        assertTrue(component.useAmbient)
    }

    @Test
    fun `LightingStateComponent reset creates new Vector3f instance`() {
        // Arrange
        val component = LightingStateComponent()
        val originalAmbient = component.ambientLight

        // Act - modify and reset
        component.ambientLight.set(1.0f, 1.0f, 1.0f)
        component.reset()

        // Assert - original reference should not be affected
        assertNotEquals(1.0f, originalAmbient.x, epsilon)
    }

    // =========================================================================
    // LIGHTINGCOMPONENT TESTS
    // =========================================================================

    @Test
    fun `LightingComponent default values are correct`() {
        // Arrange & Act
        val component = LightingComponent()

        // Assert - Sun direction (pointing down)
        assertEquals(0.0f, component.sunDirection.x, epsilon)
        assertEquals(-1.0f, component.sunDirection.y, epsilon)
        assertEquals(0.0f, component.sunDirection.z, epsilon)

        // Assert - Sun color (white)
        assertEquals(1.0f, component.sunColor.x, epsilon)
        assertEquals(1.0f, component.sunColor.y, epsilon)
        assertEquals(1.0f, component.sunColor.z, epsilon)

        // Assert - Intensities
        assertEquals(1.0f, component.sunIntensity, epsilon)
        assertEquals(1.0f, component.shadowIntensity, epsilon)
    }

    @Test
    fun `LightingComponent custom values are preserved`() {
        // Arrange & Act
        val component = LightingComponent(
            sunDirection = Vector3f(1.0f, 0.0f, 0.0f),
            sunColor = Vector3f(1.0f, 0.8f, 0.6f),
            sunIntensity = 0.5f,
            shadowIntensity = 0.3f,
        )

        // Assert
        assertEquals(1.0f, component.sunDirection.x, epsilon)
        assertEquals(1.0f, component.sunColor.x, epsilon)
        assertEquals(0.8f, component.sunColor.y, epsilon)
        assertEquals(0.6f, component.sunColor.z, epsilon)
        assertEquals(0.5f, component.sunIntensity, epsilon)
        assertEquals(0.3f, component.shadowIntensity, epsilon)
    }

    @Test
    fun `LightingComponent reset restores defaults`() {
        // Arrange
        val component = LightingComponent().apply {
            sunDirection.set(0.0f, 1.0f, 0.0f)
            sunColor.set(0.0f, 0.0f, 1.0f)
            sunIntensity = 0.0f
            shadowIntensity = 0.0f
        }

        // Act
        component.reset()

        // Assert
        assertEquals(0.0f, component.sunDirection.x, epsilon)
        assertEquals(-1.0f, component.sunDirection.y, epsilon)
        assertEquals(0.0f, component.sunDirection.z, epsilon)
        assertEquals(1.0f, component.sunColor.x, epsilon)
        assertEquals(1.0f, component.sunColor.y, epsilon)
        assertEquals(1.0f, component.sunColor.z, epsilon)
        assertEquals(1.0f, component.sunIntensity, epsilon)
        assertEquals(1.0f, component.shadowIntensity, epsilon)
    }

    @Test
    fun `LightingComponent reset creates new Vector3f instances`() {
        // Arrange
        val component = LightingComponent()
        val originalSunDirection = component.sunDirection
        val originalSunColor = component.sunColor

        // Act - modify and reset
        component.sunDirection.set(1.0f, 1.0f, 1.0f)
        component.sunColor.set(0.0f, 0.0f, 0.0f)
        component.reset()

        // Assert - original references should not be affected
        assertNotEquals(1.0f, originalSunDirection.x, epsilon)
        assertNotEquals(0.0f, originalSunColor.x, epsilon)
    }

    @Test
    fun `LightingComponent sunDirection and sunColor are independent`() {
        // Arrange
        val component = LightingComponent()
        val originalSunColor = Vector3f(component.sunColor)

        // Act
        component.sunDirection.set(1.0f, 0.0f, 0.0f)

        // Assert
        assertEquals(originalSunColor.x, component.sunColor.x, epsilon)
        assertEquals(originalSunColor.y, component.sunColor.y, epsilon)
        assertEquals(originalSunColor.z, component.sunColor.z, epsilon)
    }
}
