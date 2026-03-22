package com.pafoid.skate.engine.ecs.config

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for EnvironmentConfig data class.
 *
 * Tests cover:
 * - Default property values
 * - reset() functionality
 * - Preset application
 * - Vector3f property isolation (no reference sharing)
 */
class EnvironmentConfigTest {

    // Tolerance for floating point comparisons
    private val epsilon = 0.0001f

    // =========================================================================
    // DEFAULT VALUES TESTS
    // =========================================================================

    @Test
    fun `EnvironmentConfig default values are correct for sky properties`() {
        // Arrange & Act
        val config = EnvironmentConfig()

        // Assert - Sky color (light blue daytime sky)
        assertEquals(0.6f, config.skyColor.x, epsilon, "Sky color X should be 0.6")
        assertEquals(0.7f, config.skyColor.y, epsilon, "Sky color Y should be 0.7")
        assertEquals(0.9f, config.skyColor.z, epsilon, "Sky color Z should be 0.9")

        // Assert - Sky tint (neutral - no tint)
        assertEquals(1.0f, config.skyTint.x, epsilon, "Sky tint X should be 1.0")
        assertEquals(1.0f, config.skyTint.y, epsilon, "Sky tint Y should be 1.0")
        assertEquals(1.0f, config.skyTint.z, epsilon, "Sky tint Z should be 1.0")

        // Assert - Sky exposure and rotation
        assertEquals(1.0f, config.skyExposure, epsilon, "Sky exposure should be 1.0")
        assertEquals(0.0f, config.skyRotation, epsilon, "Sky rotation should be 0.0")
    }

    @Test
    fun `EnvironmentConfig default values are correct for fog properties`() {
        // Arrange & Act
        val config = EnvironmentConfig()

        // Assert - Fog color (light gray)
        assertEquals(0.8f, config.fogColor.x, epsilon, "Fog color X should be 0.8")
        assertEquals(0.8f, config.fogColor.y, epsilon, "Fog color Y should be 0.8")
        assertEquals(0.8f, config.fogColor.z, epsilon, "Fog color Z should be 0.8")

        // Assert - Fog density (no fog by default)
        assertEquals(0.0f, config.fogDensity, epsilon, "Fog density should be 0.0 (no fog)")

        // Assert - Fog gradient
        assertEquals(1.5f, config.fogGradient, epsilon, "Fog gradient should be 1.5")
    }

    // =========================================================================
    // RESET FUNCTIONALITY TESTS
    // =========================================================================

    @Test
    fun `reset restores all properties to default values`() {
        // Arrange - modify all properties
        val config = EnvironmentConfig().apply {
            skyColor.set(1.0f, 0.0f, 0.0f)
            skyTint.set(0.5f, 0.5f, 0.5f)
            skyExposure = 5.0f
            skyRotation = 180.0f
            fogColor.set(0.0f, 0.0f, 0.0f)
            fogDensity = 0.1f
            fogGradient = 5.0f
        }

        // Act
        config.reset()

        // Assert - Sky properties restored
        assertEquals(0.6f, config.skyColor.x, epsilon)
        assertEquals(0.7f, config.skyColor.y, epsilon)
        assertEquals(0.9f, config.skyColor.z, epsilon)
        assertEquals(1.0f, config.skyTint.x, epsilon)
        assertEquals(1.0f, config.skyTint.y, epsilon)
        assertEquals(1.0f, config.skyTint.z, epsilon)
        assertEquals(1.0f, config.skyExposure, epsilon)
        assertEquals(0.0f, config.skyRotation, epsilon)

        // Assert - Fog properties restored
        assertEquals(0.8f, config.fogColor.x, epsilon)
        assertEquals(0.8f, config.fogColor.y, epsilon)
        assertEquals(0.8f, config.fogColor.z, epsilon)
        assertEquals(0.0f, config.fogDensity, epsilon)
        assertEquals(1.5f, config.fogGradient, epsilon)
    }

    @Test
    fun `reset creates new Vector3f instances`() {
        // Arrange
        val config = EnvironmentConfig()
        val originalSkyColor = config.skyColor
        val originalFogColor = config.fogColor

        // Act - modify and reset
        config.skyColor.set(1.0f, 1.0f, 1.0f)
        config.fogColor.set(0.0f, 0.0f, 0.0f)
        config.reset()

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
        val config = EnvironmentConfig()

        // Act
        config.applyPreset(EnvironmentPreset.CLEAR_DAY)

        // Assert - Clear day should have light blue sky and minimal fog
        assertEquals(0.6f, config.skyColor.x, epsilon)
        assertEquals(0.7f, config.skyColor.y, epsilon)
        assertEquals(0.9f, config.skyColor.z, epsilon)
        assertEquals(0.0008f, config.fogDensity, 0.0001f)
    }

    @Test
    fun `applyPreset CLOUDY sets overcast values`() {
        // Arrange
        val config = EnvironmentConfig()

        // Act
        config.applyPreset(EnvironmentPreset.CLOUDY)

        // Assert - Cloudy should have gray sky and moderate fog
        assertEquals(0.5f, config.skyColor.x, epsilon)
        assertEquals(0.5f, config.skyColor.y, epsilon)
        assertEquals(0.5f, config.skyColor.z, epsilon)
        assertEquals(0.02f, config.fogDensity, 0.001f)
    }

    @Test
    fun `applyPreset FOGGY sets dense fog values`() {
        // Arrange
        val config = EnvironmentConfig()

        // Act
        config.applyPreset(EnvironmentPreset.FOGGY)

        // Assert - Foggy should have high fog density
        assertEquals(0.05f, config.fogDensity, 0.001f)
        assertEquals(0.5f, config.fogGradient, epsilon)
    }

    @Test
    fun `applyPreset SUNSET sets warm color values`() {
        // Arrange
        val config = EnvironmentConfig()

        // Act
        config.applyPreset(EnvironmentPreset.SUNSET)

        // Assert - Sunset should have warm orange/red colors
        assertEquals(0.9f, config.skyColor.x, epsilon)
        assertEquals(0.5f, config.skyColor.y, epsilon)
        assertEquals(0.3f, config.skyColor.z, epsilon)
    }

    @Test
    fun `applyPreset NO_FOG disables fog`() {
        // Arrange
        val config = EnvironmentConfig()

        // Act
        config.applyPreset(EnvironmentPreset.NO_FOG)

        // Assert - No fog preset should have zero fog density
        assertEquals(0.0f, config.fogDensity, epsilon)
    }

    // =========================================================================
    // VECTOR3F PROPERTY ISOLATION TESTS
    // =========================================================================

    @Test
    fun `modifying skyColor does not affect other Vector3f properties`() {
        // Arrange
        val config = EnvironmentConfig()
        val originalFogColor = Vector3f(config.fogColor)

        // Act
        config.skyColor.set(1.0f, 0.0f, 0.0f)

        // Assert
        assertEquals(originalFogColor.x, config.fogColor.x, epsilon)
        assertEquals(originalFogColor.y, config.fogColor.y, epsilon)
        assertEquals(originalFogColor.z, config.fogColor.z, epsilon)
    }

    @Test
    fun `modifying fogColor does not affect skyColor`() {
        // Arrange
        val config = EnvironmentConfig()
        val originalSkyColor = Vector3f(config.skyColor)

        // Act
        config.fogColor.set(0.0f, 0.0f, 0.0f)

        // Assert
        assertEquals(originalSkyColor.x, config.skyColor.x, epsilon)
        assertEquals(originalSkyColor.y, config.skyColor.y, epsilon)
        assertEquals(originalSkyColor.z, config.skyColor.z, epsilon)
    }

    @Test
    fun `custom config values are preserved`() {
        // Arrange & Act
        val config = EnvironmentConfig().apply {
            skyColor.set(0.1f, 0.2f, 0.3f)
            skyTint.set(1.5f, 1.2f, 0.8f)
            skyExposure = 2.5f
            skyRotation = 45.0f
            fogColor.set(0.9f, 0.8f, 0.7f)
            fogDensity = 0.03f
            fogGradient = 3.0f
        }

        // Assert - All custom values preserved
        assertEquals(0.1f, config.skyColor.x, epsilon)
        assertEquals(0.2f, config.skyColor.y, epsilon)
        assertEquals(0.3f, config.skyColor.z, epsilon)
        assertEquals(1.5f, config.skyTint.x, epsilon)
        assertEquals(1.2f, config.skyTint.y, epsilon)
        assertEquals(0.8f, config.skyTint.z, epsilon)
        assertEquals(2.5f, config.skyExposure, epsilon)
        assertEquals(45.0f, config.skyRotation, epsilon)
        assertEquals(0.9f, config.fogColor.x, epsilon)
        assertEquals(0.8f, config.fogColor.y, epsilon)
        assertEquals(0.7f, config.fogColor.z, epsilon)
        assertEquals(0.03f, config.fogDensity, epsilon)
        assertEquals(3.0f, config.fogGradient, epsilon)
    }
}
