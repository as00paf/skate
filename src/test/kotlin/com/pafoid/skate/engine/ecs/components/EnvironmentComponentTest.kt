package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for EnvironmentComponent.
 *
 * Tests cover:
 * - Default property values
 * - reset() functionality
 * - Preset application
 * - Vector3f property isolation
 * - renderSky and renderFog toggles
 */
class EnvironmentComponentTest {

    // Tolerance for floating point comparisons
    private val epsilon = 0.0001f

    // =========================================================================
    // DEFAULT VALUES TESTS
    // =========================================================================

    @Test
    fun `EnvironmentComponent default values are correct for sky properties`() {
        // Arrange & Act
        val component = EnvironmentComponent()

        // Assert - Sky color (light blue daytime sky)
        assertEquals(0.6f, component.skyColor.x, epsilon, "Sky color X should be 0.6")
        assertEquals(0.7f, component.skyColor.y, epsilon, "Sky color Y should be 0.7")
        assertEquals(0.9f, component.skyColor.z, epsilon, "Sky color Z should be 0.9")

        // Assert - Sky tint (neutral - no tint)
        assertEquals(1.0f, component.skyTint.x, epsilon, "Sky tint X should be 1.0")
        assertEquals(1.0f, component.skyTint.y, epsilon, "Sky tint Y should be 1.0")
        assertEquals(1.0f, component.skyTint.z, epsilon, "Sky tint Z should be 1.0")

        // Assert - Sky exposure and rotation
        assertEquals(1.0f, component.skyExposure, epsilon, "Sky exposure should be 1.0")
        assertEquals(0.0f, component.skyRotation, epsilon, "Sky rotation should be 0.0")
    }

    @Test
    fun `EnvironmentComponent default values are correct for fog properties`() {
        // Arrange & Act
        val component = EnvironmentComponent()

        // Assert - Fog color (light gray)
        assertEquals(0.8f, component.fogColor.x, epsilon, "Fog color X should be 0.8")
        assertEquals(0.8f, component.fogColor.y, epsilon, "Fog color Y should be 0.8")
        assertEquals(0.8f, component.fogColor.z, epsilon, "Fog color Z should be 0.8")

        // Assert - Fog density (no fog by default)
        assertEquals(0.0f, component.fogDensity, epsilon, "Fog density should be 0.0 (no fog)")

        // Assert - Fog gradient
        assertEquals(1.5f, component.fogGradient, epsilon, "Fog gradient should be 1.5")
    }

    @Test
    fun `EnvironmentComponent render toggles default to true`() {
        // Arrange & Act
        val component = EnvironmentComponent()

        // Assert
        assertTrue(component.renderSky, "renderSky should default to true")
        assertTrue(component.renderFog, "renderFog should default to true")
    }

    // =========================================================================
    // RESET FUNCTIONALITY TESTS
    // =========================================================================

    @Test
    fun `reset restores all properties to default values`() {
        // Arrange - modify all properties
        val component = EnvironmentComponent().apply {
            skyColor.set(1.0f, 0.0f, 0.0f)
            skyTint.set(0.5f, 0.5f, 0.5f)
            skyExposure = 5.0f
            skyRotation = 180.0f
            fogColor.set(0.0f, 0.0f, 0.0f)
            fogDensity = 0.1f
            fogGradient = 5.0f
            renderSky = false
            renderFog = false
        }

        // Act
        component.reset()

        // Assert - Sky properties restored
        assertEquals(0.6f, component.skyColor.x, epsilon)
        assertEquals(0.7f, component.skyColor.y, epsilon)
        assertEquals(0.9f, component.skyColor.z, epsilon)
        assertEquals(1.0f, component.skyTint.x, epsilon)
        assertEquals(1.0f, component.skyTint.y, epsilon)
        assertEquals(1.0f, component.skyTint.z, epsilon)
        assertEquals(1.0f, component.skyExposure, epsilon)
        assertEquals(0.0f, component.skyRotation, epsilon)

        // Assert - Fog properties restored
        assertEquals(0.8f, component.fogColor.x, epsilon)
        assertEquals(0.8f, component.fogColor.y, epsilon)
        assertEquals(0.8f, component.fogColor.z, epsilon)
        assertEquals(0.0f, component.fogDensity, epsilon)
        assertEquals(1.5f, component.fogGradient, epsilon)

        // Assert - Render toggles restored
        assertTrue(component.renderSky)
        assertTrue(component.renderFog)
    }

    @Test
    fun `reset creates new Vector3f instances`() {
        // Arrange
        val component = EnvironmentComponent()
        val originalSkyColor = component.skyColor
        val originalFogColor = component.fogColor

        // Act - modify and reset
        component.skyColor.set(1.0f, 1.0f, 1.0f)
        component.fogColor.set(0.0f, 0.0f, 0.0f)
        component.reset()

        // Assert - original references should not be affected
        assertNotEquals(1.0f, originalSkyColor.x, epsilon, "Original reference should not change")
        assertNotEquals(0.0f, originalFogColor.x, epsilon, "Original reference should not change")
    }

    // =========================================================================
    // PRESET APPLICATION TESTS
    // =========================================================================

    @Test
    fun `applyPreset CLEAR_DAY sets appropriate values`() {
        // Arrange
        val component = EnvironmentComponent()

        // Act
        component.applyPreset(EnvironmentPreset.CLEAR_DAY)

        // Assert - Clear day should have light blue sky and minimal fog
        assertEquals(0.6f, component.skyColor.x, epsilon)
        assertEquals(0.7f, component.skyColor.y, epsilon)
        assertEquals(0.9f, component.skyColor.z, epsilon)
        assertEquals(0.0008f, component.fogDensity, 0.0001f)
        assertTrue(component.renderSky)
        assertTrue(component.renderFog)
    }

    @Test
    fun `applyPreset CLOUDY sets overcast values`() {
        // Arrange
        val component = EnvironmentComponent()

        // Act
        component.applyPreset(EnvironmentPreset.CLOUDY)

        // Assert - Cloudy should have gray sky and moderate fog
        assertEquals(0.5f, component.skyColor.x, epsilon)
        assertEquals(0.5f, component.skyColor.y, epsilon)
        assertEquals(0.5f, component.skyColor.z, epsilon)
        assertEquals(0.02f, component.fogDensity, 0.001f)
    }

    @Test
    fun `applyPreset FOGGY sets dense fog values`() {
        // Arrange
        val component = EnvironmentComponent()

        // Act
        component.applyPreset(EnvironmentPreset.FOGGY)

        // Assert - Foggy should have high fog density
        assertEquals(0.05f, component.fogDensity, 0.001f)
        assertEquals(0.5f, component.fogGradient, epsilon)
    }

    @Test
    fun `applyPreset SUNSET sets warm color values`() {
        // Arrange
        val component = EnvironmentComponent()

        // Act
        component.applyPreset(EnvironmentPreset.SUNSET)

        // Assert - Sunset should have warm orange/red colors
        assertEquals(0.9f, component.skyColor.x, epsilon)
        assertEquals(0.5f, component.skyColor.y, epsilon)
        assertEquals(0.3f, component.skyColor.z, epsilon)
    }

    @Test
    fun `applyPreset NO_FOG disables fog`() {
        // Arrange
        val component = EnvironmentComponent()

        // Act
        component.applyPreset(EnvironmentPreset.NO_FOG)

        // Assert - No fog preset should have zero fog density
        assertEquals(0.0f, component.fogDensity, epsilon)
    }

    // =========================================================================
    // VECTOR3F PROPERTY ISOLATION TESTS
    // =========================================================================

    @Test
    fun `modifying skyColor does not affect other Vector3f properties`() {
        // Arrange
        val component = EnvironmentComponent()
        val originalFogColor = Vector3f(component.fogColor)

        // Act
        component.skyColor.set(1.0f, 0.0f, 0.0f)

        // Assert
        assertEquals(originalFogColor.x, component.fogColor.x, epsilon)
        assertEquals(originalFogColor.y, component.fogColor.y, epsilon)
        assertEquals(originalFogColor.z, component.fogColor.z, epsilon)
    }

    @Test
    fun `modifying fogColor does not affect skyColor`() {
        // Arrange
        val component = EnvironmentComponent()
        val originalSkyColor = Vector3f(component.skyColor)

        // Act
        component.fogColor.set(0.0f, 0.0f, 0.0f)

        // Assert
        assertEquals(originalSkyColor.x, component.skyColor.x, epsilon)
        assertEquals(originalSkyColor.y, component.skyColor.y, epsilon)
        assertEquals(originalSkyColor.z, component.skyColor.z, epsilon)
    }

    @Test
    fun `custom component values are preserved`() {
        // Arrange & Act
        val component = EnvironmentComponent().apply {
            skyColor.set(0.1f, 0.2f, 0.3f)
            skyTint.set(1.5f, 1.2f, 0.8f)
            skyExposure = 2.5f
            skyRotation = 45.0f
            fogColor.set(0.9f, 0.8f, 0.7f)
            fogDensity = 0.03f
            fogGradient = 3.0f
            renderSky = false
            renderFog = false
        }

        // Assert - All custom values preserved
        assertEquals(0.1f, component.skyColor.x, epsilon)
        assertEquals(0.2f, component.skyColor.y, epsilon)
        assertEquals(0.3f, component.skyColor.z, epsilon)
        assertEquals(1.5f, component.skyTint.x, epsilon)
        assertEquals(1.2f, component.skyTint.y, epsilon)
        assertEquals(0.8f, component.skyTint.z, epsilon)
        assertEquals(2.5f, component.skyExposure, epsilon)
        assertEquals(45.0f, component.skyRotation, epsilon)
        assertEquals(0.9f, component.fogColor.x, epsilon)
        assertEquals(0.8f, component.fogColor.y, epsilon)
        assertEquals(0.7f, component.fogColor.z, epsilon)
        assertEquals(0.03f, component.fogDensity, epsilon)
        assertEquals(3.0f, component.fogGradient, epsilon)
        assertFalse(component.renderSky)
        assertFalse(component.renderFog)
    }
}
