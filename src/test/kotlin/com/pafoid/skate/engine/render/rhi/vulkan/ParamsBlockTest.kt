package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.AttributeType
import com.pafoid.skate.engine.render.rhi.BlendFactor
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.CullMode
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Member
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Scalar
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lwjgl.vulkan.VK10

class ParamsBlockTest {
    private val block = ParamsBlock(
        176,
        listOf(
            Member("uMvp", 0, Scalar.FLOAT, 4, 4, 16),
            Member("uColor", 64, Scalar.FLOAT, 3),
            Member("uFlag", 76, Scalar.UINT, 1),
            Member("uLights", 80, Scalar.INT, 1, count = 4, arrayStride = 16),
            Member("uFactor", 144, Scalar.FLOAT, 1),
        ),
    )

    @Test
    fun NamedWrites_LandAtReflectedOffsetsInTheMembersType() {
        block.set("uColor", 3, 0.1f, 0.2f, 0.3f, 9f)
        block.set("uFlag", 1)
        block.set("uFactor", 2) // an int uploaded to a float uniform becomes 2.0
        assertEquals(listOf(0.1f, 0.2f, 0.3f), (0 until 3).map { block.data.getFloat(64 + it * 4) })
        assertEquals(1, block.data.getInt(76))
        assertEquals(2f, block.data.getFloat(144))
    }

    @Test
    fun ArrayElements_UseTheArrayStride_ByIndexOrAsOneArray() {
        block.set("uLights[2]", 7)
        assertEquals(7, block.data.getInt(80 + 32))
        block.set("uLights", intArrayOf(1, 2, 3, 4, 5))
        assertEquals(listOf(1, 2, 3, 4), (0 until 4).map { block.data.getInt(80 + it * 16) }, "extra values are dropped")
        block.set("uLights[9]", 1) // out of range: ignored
    }

    @Test
    fun Matrices_AreColumnMajor_UnknownNamesAreIgnored() {
        val m = FloatArray(16) { it.toFloat() }
        block.setMatrix4("uMvp", m)
        assertArrayEquals(m, FloatArray(16) { block.data.getFloat(it * 4) })
        block.set("uNotThere", 1f)
        block.setMatrix4("uColor", m) // not a matrix: ignored
        assertEquals(0f, block.data.getFloat(64))
    }

    @Test
    fun SamplerUnits_AreSetByNameLikeGlSamplerUniforms() {
        val units = SamplerUnits(listOf(
            SpirvReflection.Descriptor(2, "uTextures", SpirvReflection.DescriptorKind.COMBINED_IMAGE_SAMPLER, 8, SpirvReflection.ImageDim.D2),
            SpirvReflection.Descriptor(3, "uShadow", SpirvReflection.DescriptorKind.COMBINED_IMAGE_SAMPLER, 1, SpirvReflection.ImageDim.D2_ARRAY, true),
        ))
        assertEquals(true, units.set("uShadow", 5))
        assertEquals(true, units.set("uTextures", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)))
        assertEquals(true, units.set("uTextures[1]", 9))
        assertEquals(false, units.set("uColor", 1), "not a sampler: goes to the Params block")
        assertEquals(listOf(5), units.of(3).toList())
        assertEquals(listOf(0, 9, 2, 3, 4, 5, 6, 7), units.of(2).toList())
    }

    @Test
    fun Translate_StateEnumsAndWidenedFormats() {
        assertEquals(VK10.VK_COMPARE_OP_LESS_OR_EQUAL, VkTranslate.compareOp(CompareFunc.LESS_EQUAL))
        assertEquals(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VkTranslate.blendFactor(BlendFactor.ONE_MINUS_SRC_ALPHA))
        assertEquals(VK10.VK_CULL_MODE_NONE, VkTranslate.cullMode(CullMode.NONE))
        assertEquals(VK10.VK_FORMAT_R32G32B32A32_SINT, VkTranslate.attributeFormat(AttributeType.IVEC4))
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_UNORM, VkTranslate.imageFormat(PixelFormat.RGB8))
        assertEquals(16, VkTranslate.imageTexelSize(PixelFormat.RGB32F))
        assertArrayEquals(byteArrayOf(1, 2, 3, -1, 4, 5, 6, -1), VkTranslate.widenToFour(byteArrayOf(1, 2, 3, 4, 5, 6), 1, byteArrayOf(-1)))
    }
}
