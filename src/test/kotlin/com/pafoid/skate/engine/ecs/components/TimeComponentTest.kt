package com.pafoid.skate.engine.ecs.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for TimeComponent.
 *
 * Tests cover:
 * - Default property values
 * - reset() functionality
 * - getFormattedTime() helper method
 */
class TimeComponentTest {

    // Tolerance for floating point comparisons
    private val epsilon = 0.0001f

    // =========================================================================
    // DEFAULT VALUES TESTS
    // =========================================================================

    @Test
    fun `TimeComponent default values are correct`() {
        // Arrange & Act
        val component = TimeComponent()

        // Assert
        assertEquals(12.0f, component.timeOfDay, epsilon, "timeOfDay should default to 12.0 (noon)")
        assertEquals(1.0f, component.timeScale, epsilon, "timeScale should default to 1.0 (normal speed)")
    }

    @Test
    fun `TimeComponent custom values are preserved`() {
        // Arrange & Act
        val component = TimeComponent(timeOfDay = 18.5f, timeScale = 0.5f)

        // Assert
        assertEquals(18.5f, component.timeOfDay, epsilon)
        assertEquals(0.5f, component.timeScale, epsilon)
    }

    // =========================================================================
    // RESET FUNCTIONALITY TESTS
    // =========================================================================

    @Test
    fun `reset restores all properties to default values`() {
        // Arrange
        val component = TimeComponent().apply {
            timeOfDay = 3.0f
            timeScale = 0.0f
        }

        // Act
        component.reset()

        // Assert
        assertEquals(12.0f, component.timeOfDay, epsilon)
        assertEquals(1.0f, component.timeScale, epsilon)
    }

    // =========================================================================
    // FORMATTED TIME TESTS
    // =========================================================================

    @Test
    fun `getFormattedTime returns correct format for noon`() {
        // Arrange
        val component = TimeComponent(timeOfDay = 12.0f)

        // Act
        val formatted = component.getFormattedTime()

        // Assert
        assertEquals("12:00", formatted)
    }

    @Test
    fun `getFormattedTime returns correct format for midnight`() {
        // Arrange
        val component = TimeComponent(timeOfDay = 0.0f)

        // Act
        val formatted = component.getFormattedTime()

        // Assert
        assertEquals("00:00", formatted)
    }

    @Test
    fun `getFormattedTime returns correct format with minutes`() {
        // Arrange
        val component = TimeComponent(timeOfDay = 15.5f) // 3:30 PM

        // Act
        val formatted = component.getFormattedTime()

        // Assert
        assertEquals("15:30", formatted)
    }

    @Test
    fun `getFormattedTime returns correct format for early morning`() {
        // Arrange
        val component = TimeComponent(timeOfDay = 6.25f) // 6:15 AM

        // Act
        val formatted = component.getFormattedTime()

        // Assert
        assertEquals("06:15", formatted)
    }

    @Test
    fun `getFormattedTime returns correct format for evening`() {
        // Arrange
        val component = TimeComponent(timeOfDay = 19.75f) // 7:45 PM

        // Act
        val formatted = component.getFormattedTime()

        // Assert
        assertEquals("19:45", formatted)
    }

    @Test
    fun `getFormattedTime pads single digit hours`() {
        // Arrange
        val component = TimeComponent(timeOfDay = 5.0f)

        // Act
        val formatted = component.getFormattedTime()

        // Assert
        assertEquals("05:00", formatted)
    }

    @Test
    fun `getFormattedTime pads single digit minutes`() {
        // Arrange
        val component = TimeComponent(timeOfDay = 10.0833f) // 10:05 (approximately)

        // Act
        val formatted = component.getFormattedTime()

        // Assert - should have padded minutes
        assertEquals("10:04", formatted) // 0.0833 * 60 = 4.998 ≈ 4 minutes (truncated)
    }
}
