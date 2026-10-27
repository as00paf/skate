package com.pafoid.skate.engine.utils

import kotlin.math.PI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MathExtensionsTest {
    @Test
    fun MathExtensions_ToRadians_ConvertsKnownAngles() {
        assertEquals(0f, 0f.toRadiansF(), 1e-6f)
        assertEquals(PI.toFloat() / 2f, 90f.toRadiansF(), 1e-6f)
        assertEquals(-PI.toFloat(), (-180f).toRadiansF(), 1e-6f)
    }

    @Test
    fun MathExtensions_ToDegrees_RoundTripsWithToRadians() {
        assertEquals(180f, PI.toFloat().toDegreesF(), 1e-4f)
        assertEquals(37.5f, 37.5f.toRadiansF().toDegreesF(), 1e-4f)
    }
}
