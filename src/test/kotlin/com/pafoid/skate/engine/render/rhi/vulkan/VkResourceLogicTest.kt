package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Descriptor
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.DescriptorKind
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.ImageDim
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lwjgl.vulkan.VK10

/** GPU-free VK-02 logic: descriptor layouts from reflection, the per-frame ring, buffer versions, sampler state. */
class VkResourceLogicTest {
    private fun ubo(binding: Int, name: String, size: Int) = Descriptor(binding, name, DescriptorKind.UNIFORM_BUFFER, size = size)
    private fun sampler(binding: Int, name: String, count: Int = 1, dim: ImageDim = ImageDim.D2, depth: Boolean = false) =
        Descriptor(binding, name, DescriptorKind.COMBINED_IMAGE_SAMPLER, count, dim, depth)

    @Test
    fun Layouts_MergeStagesAndKeepTheLargerBlock() {
        val merged = DescriptorLayouts.merge(
            listOf(ubo(0, "Params", 192), ubo(1, "SkinBlock", 6400)),
            listOf(ubo(0, "Params", 512), sampler(3, "uAlbedo"), sampler(4, "uTextures", 8)),
        )
        assertEquals(listOf(0, 1, 3, 4), merged.map { it.binding })
        assertEquals(LayoutBinding.VERTEX or LayoutBinding.FRAGMENT, merged[0].stages)
        assertEquals(512, merged[0].descriptor.size, "a stage sizes the block up to the members it uses")
        assertEquals(LayoutBinding.VERTEX, merged[1].stages)
        assertEquals(LayoutBinding.FRAGMENT, merged[2].stages)
        assertEquals(2 to 9, DescriptorLayouts.counts(merged))
    }

    @Test
    fun Layouts_RejectABindingThatDisagreesBetweenStages_AndShareEqualLayouts() {
        assertThrows<IllegalArgumentException> {
            DescriptorLayouts.merge(listOf(ubo(2, "Lights", 64)), listOf(sampler(2, "uShadow")))
        }
        assertThrows<IllegalArgumentException> {
            DescriptorLayouts.merge(listOf(sampler(2, "a", 4)), listOf(sampler(2, "a", 8)))
        }
        val a = DescriptorLayouts.merge(listOf(ubo(0, "Params", 64)), listOf(sampler(1, "uTex")))
        val b = DescriptorLayouts.merge(listOf(ubo(0, "Params", 128)), listOf(sampler(1, "uOther")))
        assertEquals(DescriptorLayouts.key(a), DescriptorLayouts.key(b), "names and block sizes do not change the layout")
        val c = DescriptorLayouts.merge(listOf(ubo(0, "Params", 64)), listOf(ubo(0, "Params", 64), sampler(1, "uTex")))
        assertNotEquals(DescriptorLayouts.key(a), DescriptorLayouts.key(c), "stage visibility does")
    }

    @Test
    fun Ring_AlignsAndChainsChunks_ResetReusesThem() {
        val ring = RingAllocator(alignment = 256, chunkSize = 1024)
        assertEquals(0 to 0L, ring.allocate(100))
        assertEquals(0 to 256L, ring.allocate(300))
        assertEquals(1 to 0L, ring.allocate(600), "does not fit after 556: next chunk")
        assertEquals(2 to 0L, ring.allocate(5000), "bigger than a chunk: a chunk of its own")
        assertEquals(listOf(1024L, 1024L, 5120L), ring.chunks)
        assertEquals(6000L, ring.peak)

        ring.reset()
        assertEquals(0 to 0L, ring.allocate(1000))
        assertEquals(2 to 0L, ring.allocate(2000), "skips the reused chunk too small for it")
        assertEquals(3, ring.chunks.size, "no chunk added: append-only, indices stay stable")
        assertThrows<IllegalArgumentException> { RingAllocator(alignment = 48, chunkSize = 1024) }
    }

    @Test
    fun BufferVersions_PushOncePerWriteAndPerFrame() {
        val v = BufferVersions()
        var next = 0L
        val pushedSizes = ArrayList<Long>()
        fun bind(serial: Long): Pair<Long, Long> {
            v.resolve(serial) { size -> pushedSizes += size; v.record(serial, 7L, next++ * 64) }
            return v.buffer to v.offset
        }

        v.written(0, 40)
        assertEquals(7L to 0L, bind(1))
        assertEquals(7L to 0L, bind(1), "no write since: same version")
        v.written(40, 24)
        assertEquals(7L to 64L, bind(1), "a write between draws gets its own copy")
        assertEquals(7L to 128L, bind(2), "a new frame pushes into its own slot")
        assertEquals(listOf(40L, 64L, 64L), pushedSizes, "only the written extent is pushed")
        v.resized()
        bind(2)
        assertEquals(4L, pushedSizes.last(), "an empty buffer still pushes the minimum")
        assertEquals(4, v.pushes)
    }

    @Test
    fun SamplerState_MapsFiltersWrapsCompareAndMipClamp() {
        val trilinear = SamplerState.of(
            SamplerDesc(Filter.LINEAR_MIPMAP_LINEAR, Filter.LINEAR, Wrap.REPEAT, anisotropy = 16f), anisotropySupported = true, maxAnisotropy = 8f,
        )
        assertEquals(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR, trilinear.mipmapMode)
        assertEquals(SamplerState.ALL_LEVELS, trilinear.maxLod)
        assertEquals(8f, trilinear.anisotropy, "clamped to the device limit")

        val sky = SamplerState.of(SamplerDesc(wrap = Wrap.REPEAT, wrapV = Wrap.CLAMP_TO_EDGE), anisotropySupported = true, maxAnisotropy = 16f)
        assertEquals(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT, sky.addressU)
        assertEquals(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, sky.addressV)
        assertEquals(0f, sky.maxLod, "GL_LINEAR minification samples level 0 only")
        assertNull(sky.anisotropy)

        val shadow = SamplerState.of(SamplerDesc(compare = CompareFunc.LESS_EQUAL), anisotropySupported = false, maxAnisotropy = 1f)
        assertEquals(VK10.VK_COMPARE_OP_LESS_OR_EQUAL, shadow.compareOp)
        assertNull(SamplerState.of(SamplerDesc(anisotropy = 4f), anisotropySupported = false, maxAnisotropy = 16f).anisotropy)
    }
}
