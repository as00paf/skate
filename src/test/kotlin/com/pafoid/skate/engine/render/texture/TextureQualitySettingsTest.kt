package com.pafoid.skate.engine.render.texture

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextureQualitySettingsTest {
    private val device = FakeRhiDevice()
    private val previous = RhiContext.device.also { RhiContext.device = device }
    private val initialLevel = TextureQualitySettings.level

    @AfterEach
    fun restore() {
        TextureQualitySettings.apply(initialLevel, emptyList())
        RhiContext.device = previous
    }

    @Test
    fun Apply_UpdatesFilterAndAnisotropy_KeepingWrapAndMagFilter() {
        val rhi = device.createTexture(TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 64, 64, mipLevels = 7,
            sampler = SamplerDesc(Filter.LINEAR_MIPMAP_LINEAR, Filter.LINEAR, Wrap.REPEAT)))
        val texture = Texture(64, 64).also { it.rhi = rhi }
        for (level in TextureQualityLevel.entries) {
            TextureQualitySettings.apply(level, listOf(texture, Texture()))
            val expected = TextureSampling.forQuality(level, device.caps.maxAnisotropy)
            assertEquals(expected.maxAnisotropy, rhi.sampler.anisotropy, level.name)
            assertEquals(expected.minFilter.name, rhi.sampler.minFilter.name, level.name)
            assertEquals(Wrap.REPEAT, rhi.sampler.wrap)
            assertEquals(Filter.LINEAR, rhi.sampler.magFilter)
        }
    }
}
