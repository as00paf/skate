package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Descriptor
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.DescriptorKind
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.ImageDim
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which bound texture a sampler descriptor accepts (otherwise a dummy is bound); VK-05 found SSAO's depth read. */
class DescriptorCompatibilityTest {
    private fun sampler(dim: ImageDim, shadow: Boolean) = Descriptor(0, "u", DescriptorKind.COMBINED_IMAGE_SAMPLER, dim = dim, depth = shadow)
    private fun ok(type: TextureType, isDepth: Boolean, compares: Boolean, d: Descriptor) = VkFrameResources.compatible(type, isDepth, compares, d)

    @Test
    fun PlainSampler_ReadsColour() {
        assertTrue(ok(TextureType.TEXTURE_2D, isDepth = false, compares = false, sampler(ImageDim.D2, shadow = false)))
    }

    @Test
    fun PlainSampler_ReadsDepthWithoutCompare() {
        // SSAO samples the scene depth through a sampler2D
        assertTrue(ok(TextureType.TEXTURE_2D, isDepth = true, compares = false, sampler(ImageDim.D2, shadow = false)))
    }

    @Test
    fun PlainSampler_RejectsDepthWithCompare() {
        assertFalse(ok(TextureType.TEXTURE_2D, isDepth = true, compares = true, sampler(ImageDim.D2, shadow = false)))
    }

    @Test
    fun ShadowSampler_NeedsDepth() {
        assertTrue(ok(TextureType.TEXTURE_2D_ARRAY, isDepth = true, compares = true, sampler(ImageDim.D2_ARRAY, shadow = true)))
        assertFalse(ok(TextureType.TEXTURE_2D_ARRAY, isDepth = false, compares = false, sampler(ImageDim.D2_ARRAY, shadow = true)))
    }

    @Test
    fun DimensionMustMatch() {
        assertFalse(ok(TextureType.CUBE, isDepth = false, compares = false, sampler(ImageDim.D2, shadow = false)))
        assertFalse(ok(TextureType.TEXTURE_2D, isDepth = true, compares = true, sampler(ImageDim.D2_ARRAY, shadow = true)))
    }
}
