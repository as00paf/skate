package com.pafoid.skate.engine.render.renderer.passes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PostProcessQualityPresetTest {

    @Test
    fun `LOW disables all post-processing effects`() {
        val settings = PostProcessSettings()
        PostProcessQualityPreset.apply(PostProcessQuality.LOW, settings)
        assertFalse(settings.bloomEnabled)
        assertFalse(settings.ssaoEnabled)
        assertFalse(settings.fxaaEnabled)
    }

    @Test
    fun `MEDIUM enables only bloom and FXAA`() {
        val settings = PostProcessSettings()
        PostProcessQualityPreset.apply(PostProcessQuality.MEDIUM, settings)
        assertTrue(settings.bloomEnabled)
        assertEquals(0.05f, settings.bloomIntensity)
        assertFalse(settings.ssaoEnabled)
        assertTrue(settings.fxaaEnabled)
    }

    @Test
    fun `HIGH enables bloom and SSAO with standard parameters`() {
        val settings = PostProcessSettings()
        PostProcessQualityPreset.apply(PostProcessQuality.HIGH, settings)
        assertTrue(settings.bloomEnabled)
        assertEquals(0.05f, settings.bloomIntensity)
        assertTrue(settings.ssaoEnabled)
        assertEquals(1.5f, settings.ssaoRadius)
        assertEquals(0.8f, settings.ssaoIntensity)
        assertTrue(settings.fxaaEnabled)
    }

    @Test
    fun `ULTRA enables bloom and SSAO with enhanced parameters`() {
        val settings = PostProcessSettings()
        PostProcessQualityPreset.apply(PostProcessQuality.ULTRA, settings)
        assertTrue(settings.bloomEnabled)
        assertEquals(0.1f, settings.bloomIntensity)
        assertTrue(settings.ssaoEnabled)
        assertEquals(2.0f, settings.ssaoRadius)
        assertEquals(1.0f, settings.ssaoIntensity)
        assertTrue(settings.fxaaEnabled)
    }

    @Test
    fun `PostProcessQuality fromGraphicsQuality maps by name`() {
        assertEquals(PostProcessQuality.LOW, PostProcessQuality.fromGraphicsQuality("LOW"))
        assertEquals(PostProcessQuality.MEDIUM, PostProcessQuality.fromGraphicsQuality("MEDIUM"))
        assertEquals(PostProcessQuality.HIGH, PostProcessQuality.fromGraphicsQuality("HIGH"))
        assertEquals(PostProcessQuality.ULTRA, PostProcessQuality.fromGraphicsQuality("ULTRA"))
    }

    @Test
    fun `PostProcessQuality fromGraphicsQuality is case-insensitive`() {
        assertEquals(PostProcessQuality.HIGH, PostProcessQuality.fromGraphicsQuality("high"))
        assertEquals(PostProcessQuality.HIGH, PostProcessQuality.fromGraphicsQuality("High"))
    }

    @Test
    fun `PostProcessQuality fromGraphicsQuality returns DEFAULT for unknown names`() {
        assertEquals(PostProcessQuality.MEDIUM, PostProcessQuality.fromGraphicsQuality("UNKNOWN"))
        assertEquals(PostProcessQuality.MEDIUM, PostProcessQuality.fromGraphicsQuality(null ?: ""))
    }
}
