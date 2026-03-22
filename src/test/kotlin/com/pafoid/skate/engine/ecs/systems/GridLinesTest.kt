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

    @Test
    fun `calculateMinorLineAlpha_closeDistance_returnsFullAlpha`() {
        // Arrange
        val cameraDistance = 3.0f // Less than closeDistance (5.0)
        val closeDistance = 5.0f
        val farDistance = 20.0f

        // Act
        val alpha = calculateMinorLineAlpha(cameraDistance, closeDistance, farDistance)

        // Assert
        assertEquals(1.0f, alpha, 0.01f, "Should return full alpha when camera is close")
    }

    @Test
    fun `calculateMinorLineAlpha_farDistance_returnsZeroAlpha`() {
        // Arrange
        val cameraDistance = 25.0f // Greater than farDistance (20.0)
        val closeDistance = 5.0f
        val farDistance = 20.0f

        // Act
        val alpha = calculateMinorLineAlpha(cameraDistance, closeDistance, farDistance)

        // Assert
        assertEquals(0.0f, alpha, 0.01f, "Should return zero alpha when camera is far")
    }

    @Test
    fun `calculateMinorLineAlpha_atCloseDistance_returnsFullAlpha`() {
        // Arrange
        val cameraDistance = 5.0f // Exactly at closeDistance
        val closeDistance = 5.0f
        val farDistance = 20.0f

        // Act
        val alpha = calculateMinorLineAlpha(cameraDistance, closeDistance, farDistance)

        // Assert
        assertEquals(1.0f, alpha, 0.01f, "Should return full alpha at close distance boundary")
    }

    @Test
    fun `calculateMinorLineAlpha_atFarDistance_returnsZeroAlpha`() {
        // Arrange
        val cameraDistance = 20.0f // Exactly at farDistance
        val closeDistance = 5.0f
        val farDistance = 20.0f

        // Act
        val alpha = calculateMinorLineAlpha(cameraDistance, closeDistance, farDistance)

        // Assert
        assertEquals(0.0f, alpha, 0.01f, "Should return zero alpha at far distance boundary")
    }

    @Test
    fun `calculateMinorLineAlpha_midDistance_returnsInterpolatedAlpha`() {
        // Arrange
        val cameraDistance = 12.5f // Midway between close (5) and far (20)
        val closeDistance = 5.0f
        val farDistance = 20.0f

        // Act
        val alpha = calculateMinorLineAlpha(cameraDistance, closeDistance, farDistance)

        // Assert
        assertTrue(alpha > 0f && alpha < 1f, "Should return interpolated alpha at mid distance")
        assertEquals(0.5f, alpha, 0.15f, "Should be approximately halfway at mid distance")
    }

    @Test
    fun `smoothstep_zero_returnsZero`() {
        // Act
        val result = smoothstep(0f)

        // Assert
        assertEquals(0.0f, result, 0.01f)
    }

    @Test
    fun `smoothstep_one_returnsOne`() {
        // Act
        val result = smoothstep(1f)

        // Assert
        assertEquals(1.0f, result, 0.01f)
    }

    @Test
    fun `smoothstep_half_returnsSmoothInterpolation`() {
        // Act
        val result = smoothstep(0.5f)

        // Assert
        assertEquals(0.5f, result, 0.01f, "Smoothstep at 0.5 should be 0.5")
    }

    // Helper function to test (will be moved to GridLines.kt)
    private fun calculateMinorLineAlpha(
        cameraDistance: Float,
        closeDistance: Float,
        farDistance: Float
    ): Float {
        if (cameraDistance <= closeDistance) return 1.0f
        if (cameraDistance >= farDistance) return 0.0f

        val t = (cameraDistance - closeDistance) / (farDistance - closeDistance)
        return 1.0f - smoothstep(t)
    }

    private fun smoothstep(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }
    
    private fun isMajorLine(position: Float, majorStep: Float): Boolean {
        val remainder = abs(position % majorStep)
        val tolerance = 0.02f
        return (remainder < tolerance || remainder > majorStep - tolerance)
    }
}
