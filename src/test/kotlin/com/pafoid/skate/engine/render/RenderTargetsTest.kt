package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.utils.EntityIdEncoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The render-target wrappers over the RHI (REN-12 step 4), on a GL-free device. */
class RenderTargetsTest {
    private val device = FakeRhiDevice()

    @Test
    fun FrameBuffer_SceneColorAndDepth_ResizesInPlace() {
        val fb = FrameBuffer(640, 360) { device }
        assertEquals(0, fb.getTextureId())
        fb.initialize()
        val target = device.targets.single()
        assertEquals(listOf(PixelFormat.RGBA8), target.desc.colors)
        assertEquals(PixelFormat.DEPTH32F, target.desc.depth)
        assertEquals(Filter.LINEAR, target.desc.colorSampler.minFilter)
        assertEquals(Filter.NEAREST, target.desc.depthSampler.minFilter)
        assertEquals(target.colorTexture().nativeHandle.toInt(), fb.getTextureId())

        fb.resize(640, 360)
        fb.resize(800, 600)
        assertSame(target, fb.target)
        assertEquals(800 to 600, target.width to target.height)
        assertEquals(800 to 600, fb.width to fb.height)
        assertEquals(listOf("resize ${target.name} 800x600"), device.events)

        fb.destroy()
        assertTrue(target.closed)
        assertEquals(0, fb.getTextureId())
    }

    @Test
    fun ColorTarget_CreatedLazily_ClampedColorOnly() {
        val ct = ColorTarget { device }
        assertEquals(0, ct.textureId)
        assertTrue(device.targets.isEmpty())

        ct.ensure(320, 200)
        ct.ensure(320, 200)
        val target = device.targets.single()
        assertNull(target.desc.depth)
        assertEquals(Wrap.CLAMP_TO_EDGE, target.desc.colorSampler.wrap)
        assertEquals(320, ct.width)

        ct.destroy()
        assertTrue(target.closed)
        assertEquals(0, ct.width)
    }

    @Test
    fun PickingTexture_ReadPixel_DecodesTheIdOfOneFloatPixel() {
        val picking = PickingTexture(100, 50) { device }
        device.readbackValue = EntityIdEncoder.encode(42)

        assertEquals(42, picking.readPixel(7, 9))
        val name = device.targets.single().name
        assertEquals("read $name IntRect(x=7, y=9, width=1, height=1) RGB32F", device.events.last())
        assertEquals(listOf(PixelFormat.RGB32F), picking.target.desc.colors)
    }

    @Test
    fun ShadowMap_Ensure_ClampsToCapsAndReallocatesOnlyOnChange() {
        val map = ShadowMap { device }
        assertFalse(map.isInitialized())
        assertEquals(0, map.getDepthTextureId())

        assertTrue(map.ensure(8192, 400))
        val first = device.targets.single()
        assertEquals(device.caps.maxTextureSize, map.resolution)
        assertEquals(device.caps.maxArrayLayers, map.layers)
        assertTrue(first.desc.layered)
        assertTrue(first.desc.colors.isEmpty())
        assertEquals(CompareFunc.LESS_EQUAL, first.desc.depthSampler.compare)
        assertEquals(TextureType.TEXTURE_2D_ARRAY, first.depthTexture()!!.desc.type)

        assertFalse(map.ensure(8192, 400))
        assertTrue(map.ensure(1024, 1))
        assertTrue(first.closed)
        val second = device.targets.last()
        assertEquals(TextureType.TEXTURE_2D_ARRAY, second.depthTexture()!!.desc.type)
        assertEquals(second.depthTexture()!!.nativeHandle.toInt(), map.getDepthTextureId())
    }
}
