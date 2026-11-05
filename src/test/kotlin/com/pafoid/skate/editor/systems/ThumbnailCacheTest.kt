package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.render.renderer.ThumbnailRenderer
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThumbnailCacheTest {
    private val device = FakeRhiDevice()
    private val renderer = mockk<ThumbnailRenderer>(relaxed = true).also {
        every { it.renderThumbnail(any()) } answers {
            device.createTexture(TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 256, 256))
        }
    }
    private val cache = ThumbnailCache(renderer)

    @Test
    fun Thumbnail_IsRenderedOnce_AndReturnsTheImGuiHandle() {
        val model = `3dModel`("m.gltf")
        val handle = cache.getThumbnail("m", model)
        assertEquals(handle, cache.getThumbnail("m", model))
        verify(exactly = 1) { renderer.renderThumbnail(model) }
        assertEquals(device.textures.single().nativeHandle.toInt(), handle)
    }

    @Test
    fun Destroy_ClosesEveryThumbnail() {
        cache.getThumbnail("a", `3dModel`("a.gltf"))
        cache.getThumbnail("b", `3dModel`("b.gltf"))
        cache.destroy()
        assertTrue(device.textures.all { it.closed })
        verify { renderer.destroy() }
    }
}
