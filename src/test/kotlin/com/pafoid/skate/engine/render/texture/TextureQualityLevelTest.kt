package com.pafoid.skate.engine.render.texture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextureQualityLevelTest {

    @Test
    fun `fromName maps by name`() {
        assertEquals(TextureQualityLevel.LOW, TextureQualityLevel.fromName("LOW"))
        assertEquals(TextureQualityLevel.MEDIUM, TextureQualityLevel.fromName("MEDIUM"))
        assertEquals(TextureQualityLevel.HIGH, TextureQualityLevel.fromName("HIGH"))
    }

    @Test
    fun `fromName is case-insensitive`() {
        assertEquals(TextureQualityLevel.HIGH, TextureQualityLevel.fromName("high"))
        assertEquals(TextureQualityLevel.HIGH, TextureQualityLevel.fromName("High"))
    }

    @Test
    fun `fromName returns DEFAULT for unknown names`() {
        assertEquals(TextureQualityLevel.MEDIUM, TextureQualityLevel.fromName("UNKNOWN"))
        assertEquals(TextureQualityLevel.MEDIUM, TextureQualityLevel.fromName(null ?: ""))
    }

    @Test
    fun `minFilter matches expected filter per tier`() {
        assertEquals(MinFilter.NEAREST_MIPMAP_LINEAR, TextureQualityLevel.LOW.minFilter)
        assertEquals(MinFilter.LINEAR_MIPMAP_LINEAR, TextureQualityLevel.MEDIUM.minFilter)
        assertEquals(MinFilter.LINEAR_MIPMAP_LINEAR, TextureQualityLevel.HIGH.minFilter)
    }

    @Test
    fun `maxAnisotropy matches expected value per tier`() {
        assertEquals(1f, TextureQualityLevel.LOW.maxAnisotropy)
        assertEquals(4f, TextureQualityLevel.MEDIUM.maxAnisotropy)
        assertEquals(16f, TextureQualityLevel.HIGH.maxAnisotropy)
    }

    @Test
    fun `generateMipmaps is true for all tiers`() {
        assertTrue(TextureQualityLevel.LOW.generateMipmaps)
        assertTrue(TextureQualityLevel.MEDIUM.generateMipmaps)
        assertTrue(TextureQualityLevel.HIGH.generateMipmaps)
    }
}
