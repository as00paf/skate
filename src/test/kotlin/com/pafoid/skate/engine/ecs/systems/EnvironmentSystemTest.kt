package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.config.EnvironmentConfig
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Unit tests for EnvironmentSystem.
 *
 * Tests cover:
 * - System initialization with default and custom config
 * - Config ownership and access
 * - Preset application
 * - Reset functionality
 */
class EnvironmentSystemTest {

    // Mock dependencies
    private val stringManager: StringManager = mockk()

    // =========================================================================
    // INITIALIZATION TESTS
    // =========================================================================

    @Test
    fun `EnvironmentSystem initializes with default config`() {
        // Arrange & Act
        val system = EnvironmentSystem(stringManager = stringManager)

        // Assert
        assertNotNull(system.config, "Config should not be null")
        assertEquals(0.6f, system.config.skyColor.x, 0.0001f)
        assertEquals(0.0f, system.config.fogDensity, 0.0001f)
    }

    @Test
    fun `EnvironmentSystem initializes with custom config`() {
        // Arrange
        val customConfig = EnvironmentConfig().apply {
            skyColor.set(1.0f, 0.0f, 0.0f)
            fogDensity = 0.5f
        }

        // Act
        val system = EnvironmentSystem(initialConfig = customConfig, stringManager = stringManager)

        // Assert
        assertSame(customConfig, system.config, "System should use provided config")
        assertEquals(1.0f, system.config.skyColor.x, 0.0001f)
        assertEquals(0.5f, system.config.fogDensity, 0.0001f)
    }

    @Test
    fun `EnvironmentSystem has EARLY execution priority`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)

        // Act
        val priority = system.priority

        // Assert
        assertEquals(ExecutionPriority.EARLY, priority, "EnvironmentSystem should run EARLY")
    }

    // =========================================================================
    // PRESET APPLICATION TESTS
    // =========================================================================

    @Test
    fun `applyPreset updates config with preset values`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val initialFogDensity = system.config.fogDensity

        // Act
        system.applyPreset(EnvironmentPreset.FOGGY)

        // Assert
        assertNotEquals(initialFogDensity, system.config.fogDensity, 0.0001f)
        assertEquals(0.05f, system.config.fogDensity, 0.001f)
    }

    @Test
    fun `applyPreset CLEAR_DAY sets correct values`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)

        // Act
        system.applyPreset(EnvironmentPreset.CLEAR_DAY)

        // Assert
        assertEquals(0.6f, system.config.skyColor.x, 0.0001f)
        assertEquals(0.0008f, system.config.fogDensity, 0.0001f)
    }

    @Test
    fun `applyPreset SUNSET sets warm colors`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)

        // Act
        system.applyPreset(EnvironmentPreset.SUNSET)

        // Assert
        assertEquals(0.9f, system.config.skyColor.x, 0.0001f)
        assertEquals(0.5f, system.config.skyColor.y, 0.0001f)
        assertEquals(0.3f, system.config.skyColor.z, 0.0001f)
    }

    @Test
    fun `applyPreset NO_FOG disables fog`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)

        // Act
        system.applyPreset(EnvironmentPreset.NO_FOG)

        // Assert
        assertEquals(0.0f, system.config.fogDensity, 0.0001f)
    }

    // =========================================================================
    // RESET FUNCTIONALITY TESTS
    // =========================================================================

    @Test
    fun `reset restores config to default values`() {
        // Arrange - modify config
        val system = EnvironmentSystem(stringManager = stringManager)
        system.config.skyColor.set(1.0f, 1.0f, 1.0f)
        system.config.fogDensity = 0.1f
        system.config.skyExposure = 5.0f

        // Act
        system.reset()

        // Assert - defaults restored
        assertEquals(0.6f, system.config.skyColor.x, 0.0001f)
        assertEquals(0.0f, system.config.fogDensity, 0.0001f)
        assertEquals(1.0f, system.config.skyExposure, 0.0001f)
    }

    @Test
    fun `reset after preset application restores defaults`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)

        // Act - apply preset then reset
        system.applyPreset(EnvironmentPreset.FOGGY)
        system.reset()

        // Assert
        assertEquals(0.0f, system.config.fogDensity, 0.0001f)
        assertEquals(1.5f, system.config.fogGradient, 0.0001f)
    }

    // =========================================================================
    // CONFIG ACCESS TESTS
    // =========================================================================

    @Test
    fun `config is mutable after initialization`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)

        // Act
        system.config.skyColor.set(0.5f, 0.5f, 0.5f)
        system.config.fogDensity = 0.02f
        system.config.skyExposure = 2.0f

        // Assert
        assertEquals(0.5f, system.config.skyColor.x, 0.0001f)
        assertEquals(0.5f, system.config.skyColor.y, 0.0001f)
        assertEquals(0.5f, system.config.skyColor.z, 0.0001f)
        assertEquals(0.02f, system.config.fogDensity, 0.0001f)
        assertEquals(2.0f, system.config.skyExposure, 0.0001f)
    }

    @Test
    fun `config properties are independent`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val originalFogX = system.config.fogColor.x
        val originalFogY = system.config.fogColor.y
        val originalFogZ = system.config.fogColor.z

        // Act - modify sky color
        system.config.skyColor.set(1.0f, 0.0f, 0.0f)

        // Assert - fog color unchanged
        assertEquals(originalFogX, system.config.fogColor.x, 0.0001f)
        assertEquals(originalFogY, system.config.fogColor.y, 0.0001f)
        assertEquals(originalFogZ, system.config.fogColor.z, 0.0001f)
    }
}
