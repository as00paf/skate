package com.pafoid.skate.engine.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class NoiseGeneratorTest {

    @Test
    fun testGenerateCloudNoise() {
        val size = 32
        val buffer = NoiseGenerator.generateCloudNoise(size)
        
        assertNotNull(buffer, "Buffer should not be null")
        assertTrue(buffer.limit() > 0, "Buffer should have content")
        
        var hasNonZero = false
        var hasVariation = false
        val firstVal = buffer.get(0)
        
        while (buffer.hasRemaining()) {
            val b = buffer.get()
            if (b.toInt() != 0) hasNonZero = true
            if (b != firstVal) hasVariation = true
        }
        
        assertTrue(hasNonZero, "Noise buffer should contain non-zero values")
        assertTrue(hasVariation, "Noise buffer should contain varying values")
    }
}
