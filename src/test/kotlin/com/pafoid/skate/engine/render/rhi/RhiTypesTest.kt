package com.pafoid.skate.engine.render.rhi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RhiTypesTest {

    @Test
    fun BlendState_KeepTargetOpaque_MatchesTheRen19Factors() {
        // Same factors as setAlphaBlendKeepingTargetOpaque: straight alpha for colour, accumulate-only alpha
        val b = BlendState.ALPHA_KEEP_TARGET_OPAQUE
        assertTrue(b.enabled)
        assertEquals(listOf(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA),
            listOf(b.srcRgb, b.dstRgb, b.srcAlpha, b.dstAlpha))
        assertFalse(BlendState.OPAQUE.enabled)
    }

    @Test
    fun DepthState_Presets() {
        assertEquals(DepthState(true, true, CompareFunc.LESS, false), DepthState.READ_WRITE_LESS)
        assertEquals(CompareFunc.LESS_EQUAL, DepthState.READ_ONLY_LESS_EQUAL.func)
        assertFalse(DepthState.READ_ONLY_LESS_EQUAL.write)
        assertFalse(DepthState.DISABLED.test)
    }

    @Test
    fun ClearValues_ComparesColorByContentAndValidates() {
        assertEquals(ClearValues(floatArrayOf(0f, 0f, 0f, 1f), 1f), ClearValues.COLOR_AND_DEPTH)
        assertEquals(ClearValues(floatArrayOf(0f, 0f, 0f, 1f), 1f).hashCode(), ClearValues.COLOR_AND_DEPTH.hashCode())
        assertThrows<IllegalArgumentException> { ClearValues(floatArrayOf(0f, 0f, 0f)) }
        assertThrows<IllegalArgumentException> { ClearValues(depth = 2f) }
    }

    @Test
    fun IntRect_RejectsNegativeSizes() {
        assertTrue(IntRect(0, 0, 0, 10).isEmpty)
        assertThrows<IllegalArgumentException> { IntRect(0, 0, -1, 1) }
    }

    @Test
    fun TextureDesc_OnlyArraysAndCubesHaveLayers() {
        TextureDesc(TextureType.TEXTURE_2D_ARRAY, PixelFormat.DEPTH32F, 2048, 2048, layers = 4,
            sampler = SamplerDesc(compare = CompareFunc.LESS_EQUAL))
        TextureDesc(TextureType.CUBE, PixelFormat.RGBA8, 512, 512, layers = 6)
        assertThrows<IllegalArgumentException> { TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 4, 4, layers = 2) }
        assertThrows<IllegalArgumentException> { TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 0, 4) }
    }

    @Test
    fun TextureDesc_FullMipChainAndItsLimit() {
        assertEquals(1, TextureDesc.fullMipChain(1, 1))
        assertEquals(9, TextureDesc.fullMipChain(256, 200))
        assertEquals(11, TextureDesc.fullMipChain(1024, 1))
        TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 256, 256, mipLevels = 9)
        assertThrows<IllegalArgumentException> { TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 256, 256, mipLevels = 10) }
    }

    @Test
    fun RenderTargetDesc_ChecksAttachmentFormats() {
        // Scene target, picking target (RGB32F, own depth) and the shadow cascade array (depth only)
        RenderTargetDesc(1920, 1080)
        RenderTargetDesc(1920, 1080, colors = listOf(PixelFormat.RGB32F))
        RenderTargetDesc(2048, 2048, colors = emptyList(), depth = PixelFormat.DEPTH32F, layers = 4)
        assertThrows<IllegalArgumentException> { RenderTargetDesc(8, 8, colors = listOf(PixelFormat.DEPTH32F)) }
        assertThrows<IllegalArgumentException> { RenderTargetDesc(8, 8, depth = PixelFormat.RGBA8) }
        assertThrows<IllegalArgumentException> { RenderTargetDesc(8, 8, colors = emptyList(), depth = null) }
    }

    @Test
    fun RenderTargetDesc_LayeredTargetsUseArrayAttachments() {
        val shadow = RenderTargetDesc(512, 512, colors = emptyList(), layers = 1, layered = true)
        assertEquals(TextureType.TEXTURE_2D_ARRAY, shadow.depthTexture()!!.type)
        assertEquals(1, shadow.depthTexture()!!.layers)
        val scene = RenderTargetDesc(64, 32, colorSampler = SamplerDesc(wrap = Wrap.CLAMP_TO_EDGE))
        assertEquals(TextureType.TEXTURE_2D, scene.colorTexture(0).type)
        assertEquals(Wrap.CLAMP_TO_EDGE, scene.colorTexture(0).sampler.wrap)
        assertEquals(64, scene.colorTexture(0).width)
        assertThrows<IllegalArgumentException> { RenderTargetDesc(8, 8, layers = 2, layered = false) }
    }

    @Test
    fun VertexLayoutDesc_RejectsDuplicateLocationsAcrossBindings() {
        val mesh = VertexBinding(32, listOf(VertexAttribute(0, AttributeType.VEC3, 0), VertexAttribute(1, AttributeType.VEC2, 12)))
        val instances = VertexBinding(16, listOf(VertexAttribute(2, AttributeType.VEC4, 0)), perInstance = true)
        VertexLayoutDesc(listOf(mesh, instances))
        assertThrows<IllegalArgumentException> {
            VertexLayoutDesc(listOf(mesh, VertexBinding(4, listOf(VertexAttribute(1, AttributeType.FLOAT, 0)))))
        }
    }

    @Test
    fun BufferDesc_NeedsAPositiveSize() {
        assertThrows<IllegalArgumentException> { BufferDesc(BufferUsage.VERTEX, 0) }
        assertEquals(BufferUpdate.STATIC, BufferDesc(BufferUsage.INDEX, 4).update)
    }
}
