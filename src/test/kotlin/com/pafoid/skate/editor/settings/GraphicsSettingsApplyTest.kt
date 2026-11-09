package com.pafoid.skate.editor.settings

import com.pafoid.skate.engine.render.MsaaSettings
import com.pafoid.skate.engine.render.shadow.ShadowQualityLevel
import com.pafoid.skate.engine.render.shadow.ShadowSettings
import com.pafoid.skate.engine.render.texture.TextureQualityLevel
import com.pafoid.skate.engine.render.texture.TextureQualitySettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GraphicsSettingsApplyTest {

    private val initialMsaa = MsaaSettings.samples
    private val initialShadow = ShadowSettings.quality
    private val initialTexture = TextureQualitySettings.level

    @AfterEach
    fun restore() {
        MsaaSettings.samples = initialMsaa
        ShadowSettings.quality = initialShadow
        TextureQualitySettings.level = initialTexture
    }

    @Test
    fun `default GraphicsSettings maps to engine defaults`() {
        val settings = GraphicsSettings()
        assertEquals(ShadowQuality.MEDIUM, settings.shadowQuality)
        assertEquals(4, settings.msaa)
        assertEquals(TextureQuality.HIGH, settings.textureQuality)
        assertEquals(GraphicsQuality.HIGH, settings.quality)
    }

    @Test
    fun `shadowQualityLevel maps correctly`() {
        assertEquals(
            ShadowQualityLevel.HIGH,
            GraphicsSettings(shadowQuality = ShadowQuality.HIGH).shadowQualityLevel
        )
        assertEquals(
            ShadowQualityLevel.MEDIUM,
            GraphicsSettings(shadowQuality = ShadowQuality.MEDIUM).shadowQualityLevel
        )
        assertEquals(
            ShadowQualityLevel.LOW,
            GraphicsSettings(shadowQuality = ShadowQuality.LOW).shadowQualityLevel
        )
    }

    @Test
    fun `textureQualityLevel maps correctly`() {
        assertEquals(
            TextureQualityLevel.HIGH,
            GraphicsSettings(textureQuality = TextureQuality.HIGH).textureQualityLevel
        )
        assertEquals(
            TextureQualityLevel.MEDIUM,
            GraphicsSettings(textureQuality = TextureQuality.MEDIUM).textureQualityLevel
        )
        assertEquals(
            TextureQualityLevel.LOW,
            GraphicsSettings(textureQuality = TextureQuality.LOW).textureQualityLevel
        )
    }

    @Test
    fun `applyToEngine sets all engine static settings`() {
        GraphicsSettings(
            quality = GraphicsQuality.HIGH,
            msaa = 8,
            shadowQuality = ShadowQuality.HIGH,
            textureQuality = TextureQuality.HIGH
        ).applyToEngine()

        assertEquals(8, MsaaSettings.samples)
        assertEquals(ShadowQualityLevel.HIGH, ShadowSettings.quality)
        assertEquals(TextureQualityLevel.HIGH, TextureQualitySettings.level)
    }

    @Test
    fun `applyToEngine clamps MSAA to 0-8 range`() {
        GraphicsSettings(msaa = 16).applyToEngine()
        assertEquals(8, MsaaSettings.samples)

        GraphicsSettings(msaa = -4).applyToEngine()
        assertEquals(0, MsaaSettings.samples)
    }

    @Test
    fun `applyToEngine with LOW quality still sets shadow and texture`() {
        GraphicsSettings(
            quality = GraphicsQuality.LOW,
            msaa = 2,
            shadowQuality = ShadowQuality.LOW,
            textureQuality = TextureQuality.LOW
        ).applyToEngine()

        assertEquals(2, MsaaSettings.samples)
        assertEquals(ShadowQualityLevel.LOW, ShadowSettings.quality)
        assertEquals(TextureQualityLevel.LOW, TextureQualitySettings.level)
    }

    @Test
    fun `postProcessQuality maps from GraphicsQuality correctly`() {
        assertEquals(
            com.pafoid.skate.engine.render.renderer.passes.PostProcessQuality.HIGH,
            GraphicsSettings(quality = GraphicsQuality.HIGH).postProcessQuality
        )
        assertEquals(
            com.pafoid.skate.engine.render.renderer.passes.PostProcessQuality.LOW,
            GraphicsSettings(quality = GraphicsQuality.LOW).postProcessQuality
        )
        assertEquals(
            com.pafoid.skate.engine.render.renderer.passes.PostProcessQuality.ULTRA,
            GraphicsSettings(quality = GraphicsQuality.ULTRA).postProcessQuality
        )
    }

    @Test
    fun `TextureQuality toLevel maps by name`() {
        assertEquals(TextureQualityLevel.LOW, TextureQuality.LOW.toLevel())
        assertEquals(TextureQualityLevel.MEDIUM, TextureQuality.MEDIUM.toLevel())
        assertEquals(TextureQualityLevel.HIGH, TextureQuality.HIGH.toLevel())
    }

    @Test
    fun `TextureQualityLevel toSetting maps by name`() {
        assertEquals(TextureQuality.LOW, TextureQualityLevel.LOW.toSetting())
        assertEquals(TextureQuality.MEDIUM, TextureQualityLevel.MEDIUM.toSetting())
        assertEquals(TextureQuality.HIGH, TextureQualityLevel.HIGH.toSetting())
    }
}
