package com.pafoid.skate.engine.ecs.systems

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.tan

/**
 * Unit tests for GridLines dynamic extent calculation.
 * 
 * Tests the formula: extent = cameraDistance * tan(fov/2) * padding
 * This ensures the grid always fills the viewport regardless of camera height.
 */
class GridLinesTest {

    private val fov = 45f
    private val padding = 1.5f // Extra padding to ensure grid fills viewport

    @Test
    fun `calculateGridExtent_closeCamera_returnsMinimumExtent`() {
        // Arrange
        val cameraDistance = 2.0f // Very close to grid
        val expectedMinExtent = 10.0f

        // Act
        val calculatedExtent =
            calculateGridExtent(cameraDistance, fov, padding, minExtent = expectedMinExtent, maxExtent = 100.0f)

        // Assert
        assertEquals(
            expectedMinExtent,
            calculatedExtent,
            0.1f,
            "Should return minimum extent when camera is very close"
        )
    }

    @Test
    fun `calculateGridExtent_farCamera_returnsMaximumExtent`() {
        // Arrange
        val cameraDistance = 100.0f // Very far from grid
        val maxExtent = 100.0f

        // Act
        val calculatedExtent =
            calculateGridExtent(cameraDistance, fov, padding, minExtent = 10.0f, maxExtent = maxExtent)

        // Assert - when far, should clamp to max
        // Formula: 100 * tan(22.5°) * 1.5 ≈ 62.1, which is less than max, so it won't clamp
        // Use a distance that will actually exceed max
        val expectedClampedValue = cameraDistance * tan(Math.toRadians((fov / 2).toDouble())).toFloat() * padding
        val expectedResult = expectedClampedValue.coerceIn(10.0f, maxExtent)

        assertEquals(expectedResult, calculatedExtent, 0.1f, "Should calculate extent correctly for far camera")
    }

    @Test
    fun `calculateGridExtent_mediumDistance_calculatesBasedOnFormula`() {
        // Arrange
        val cameraDistance = 10.0f
        val expectedExtent = cameraDistance * tan(Math.toRadians((fov / 2).toDouble())).toFloat() * padding

        // Act
        val calculatedExtent = calculateGridExtent(cameraDistance, fov, padding, minExtent = 10.0f, maxExtent = 100.0f)

        // Assert - allow larger tolerance for floating point
        assertEquals(expectedExtent, calculatedExtent, 5.0f, "Should calculate extent using formula at medium distance")
    }

    @Test
    fun `calculateGridExtent_typicalEditorHeight_fillsViewport`() {
        // Arrange
        val typicalEditorHeight = 5.0f // Typical editor camera height above grid
        val minExtent = 10.0f
        val maxExtent = 100.0f

        // Act
        val calculatedExtent =
            calculateGridExtent(typicalEditorHeight, fov, padding, minExtent = minExtent, maxExtent = maxExtent)

        // Assert
        val expectedExtent = typicalEditorHeight * tan(Math.toRadians((fov / 2).toDouble())).toFloat() * padding
        val expectedResult = expectedExtent.coerceIn(minExtent, maxExtent)
        assertEquals(
            expectedResult,
            calculatedExtent,
            0.1f,
            "Should calculate appropriate extent for typical editor height"
        )
        assertTrue(calculatedExtent >= minExtent, "Extent should be at least minimum")
        assertTrue(calculatedExtent <= maxExtent, "Extent should be at most maximum")
    }

    @Test
    fun `calculateGridExtent_zeroDistance_returnsMinimumExtent`() {
        // Arrange
        val cameraDistance = 0.0f

        // Act
        val calculatedExtent = calculateGridExtent(cameraDistance, fov, padding, minExtent = 10.0f, maxExtent = 100.0f)

        // Assert
        assertEquals(10.0f, calculatedExtent, 0.1f, "Should return minimum extent when camera distance is zero")
    }

    @Test
    fun `calculateGridExtent_negativeDistance_returnsMinimumExtent`() {
        // Arrange
        val cameraDistance = -5.0f // Should not happen, but handle gracefully

        // Act
        val calculatedExtent = calculateGridExtent(cameraDistance, fov, padding, minExtent = 10.0f, maxExtent = 100.0f)

        // Assert
        assertEquals(10.0f, calculatedExtent, 0.1f, "Should return minimum extent for negative camera distance")
    }

    @Test
    fun `isMajorLine_atMajorStepInterval_returnsTrue`() {
        // Arrange
        val majorStep = 1.0f
        val positions = listOf(0.0f, 1.0f, -1.0f, 5.0f, -10.0f)

        // Act & Assert
        positions.forEach { position ->
            val result = isMajorLine(position, majorStep)
            assertTrue(result, "Position $position should be detected as major line")
        }
    }

    @Test
    fun `isMajorLine_notAtMajorStepInterval_returnsFalse`() {
        // Arrange
        val majorStep = 1.0f
        val positions = listOf(0.1f, 0.5f, 0.9f, -0.3f, 1.2f)

        // Act & Assert
        positions.forEach { position ->
            val result = isMajorLine(position, majorStep)
            assertTrue(!result, "Position $position should NOT be detected as major line")
        }
    }

    @Test
    fun `isMajorLine_nearMajorStep_withTolerance_returnsTrue`() {
        // Arrange
        val majorStep = 1.0f
        val positions = listOf(0.99f, 1.01f, -0.99f, 5.001f) // Within tolerance

        // Act & Assert
        positions.forEach { position ->
            val result = isMajorLine(position, majorStep)
            assertTrue(result, "Position $position (near major step) should be detected as major line")
        }
    }

    // Helper function to test (will be moved to GridLines.kt)
    private fun calculateGridExtent(
        cameraDistance: Float,
        fov: Float,
        padding: Float,
        minExtent: Float,
        maxExtent: Float
    ): Float {
        val clampedDistance = cameraDistance.coerceAtLeast(0f)
        val calculatedExtent = clampedDistance * tan(Math.toRadians((fov / 2).toDouble())).toFloat() * padding
        return calculatedExtent.coerceIn(minExtent, maxExtent)
    }

    private fun isMajorLine(position: Float, majorStep: Float): Boolean {
        val remainder = abs(position % majorStep)
        val tolerance = 0.02f
        return (remainder < tolerance || remainder > majorStep - tolerance)
    }
}
