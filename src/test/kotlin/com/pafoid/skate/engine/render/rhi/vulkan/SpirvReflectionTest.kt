package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Companion.DECORATION_ARRAY_STRIDE
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Companion.DECORATION_BINDING
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Companion.DECORATION_LOCATION
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Companion.DECORATION_MATRIX_STRIDE
import com.pafoid.skate.engine.render.rhi.vulkan.SpirvReflection.Companion.DECORATION_OFFSET
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Reflection of a hand-assembled module: a Params block, a sampler array, a shadow sampler and a vertex input. */
class SpirvReflectionTest {
    private val words = ArrayList<Int>()

    private fun op(code: Int, vararg operands: Int) {
        words += ((operands.size + 1) shl 16) or code
        words.addAll(operands.toList())
    }

    private fun str(s: String): IntArray {
        val bytes = s.toByteArray() + 0
        val padded = bytes.copyOf((bytes.size + 3) / 4 * 4)
        return IntArray(padded.size / 4) { i -> (0 until 4).sumOf { b -> (padded[i * 4 + b].toInt() and 0xFF) shl (b * 8) } }
    }

    private fun module(): IntArray {
        words.addAll(listOf(SpirvReflection.MAGIC, 0x00010300, 0, 100, 0))
        // ids: 1 float, 2 int, 3 vec3, 4 vec4, 5 mat4, 6 const 4, 7 int[4], 8 Params struct, 9 ptr Uniform, 10 var,
        // 11 image 2D, 12 sampled, 13 const 8, 14 sampled[8], 15 ptr UniformConstant, 16 var, 17 image 2D array depth,
        // 18 sampled, 19 ptr, 20 var, 21 ptr Input vec3, 22 var
        op(SpirvReflection.OP_NAME, 8, *str("Params"))
        op(SpirvReflection.OP_MEMBER_NAME, 8, 0, *str("uMvp"))
        op(SpirvReflection.OP_MEMBER_NAME, 8, 1, *str("uColor"))
        op(SpirvReflection.OP_MEMBER_NAME, 8, 2, *str("uLights"))
        op(SpirvReflection.OP_NAME, 16, *str("uTextures"))
        op(SpirvReflection.OP_NAME, 20, *str("uShadow"))
        op(SpirvReflection.OP_DECORATE, 7, DECORATION_ARRAY_STRIDE, 16)
        op(SpirvReflection.OP_MEMBER_DECORATE, 8, 0, DECORATION_OFFSET, 0)
        op(SpirvReflection.OP_MEMBER_DECORATE, 8, 0, DECORATION_MATRIX_STRIDE, 16)
        op(SpirvReflection.OP_MEMBER_DECORATE, 8, 1, DECORATION_OFFSET, 64)
        op(SpirvReflection.OP_MEMBER_DECORATE, 8, 2, DECORATION_OFFSET, 80)
        op(SpirvReflection.OP_DECORATE, 10, DECORATION_BINDING, 0)
        op(SpirvReflection.OP_DECORATE, 16, DECORATION_BINDING, 2)
        op(SpirvReflection.OP_DECORATE, 20, DECORATION_BINDING, 3)
        op(SpirvReflection.OP_DECORATE, 22, DECORATION_LOCATION, 0)
        op(SpirvReflection.OP_TYPE_FLOAT, 1, 32)
        op(SpirvReflection.OP_TYPE_INT, 2, 32, 1)
        op(SpirvReflection.OP_TYPE_VECTOR, 3, 1, 3)
        op(SpirvReflection.OP_TYPE_VECTOR, 4, 1, 4)
        op(SpirvReflection.OP_TYPE_MATRIX, 5, 4, 4)
        op(SpirvReflection.OP_CONSTANT, 2, 6, 4)
        op(SpirvReflection.OP_TYPE_ARRAY, 7, 2, 6)
        op(SpirvReflection.OP_TYPE_STRUCT, 8, 5, 3, 7)
        op(SpirvReflection.OP_TYPE_POINTER, 9, SpirvReflection.STORAGE_UNIFORM, 8)
        op(SpirvReflection.OP_VARIABLE, 9, 10, SpirvReflection.STORAGE_UNIFORM)
        op(SpirvReflection.OP_TYPE_IMAGE, 11, 1, 1, 0, 0, 0, 1, 0)
        op(SpirvReflection.OP_TYPE_SAMPLED_IMAGE, 12, 11)
        op(SpirvReflection.OP_CONSTANT, 2, 13, 8)
        op(SpirvReflection.OP_TYPE_ARRAY, 14, 12, 13)
        op(SpirvReflection.OP_TYPE_POINTER, 15, SpirvReflection.STORAGE_UNIFORM_CONSTANT, 14)
        op(SpirvReflection.OP_VARIABLE, 15, 16, SpirvReflection.STORAGE_UNIFORM_CONSTANT)
        op(SpirvReflection.OP_TYPE_IMAGE, 17, 1, 1, 1, 1, 0, 1, 0)
        op(SpirvReflection.OP_TYPE_SAMPLED_IMAGE, 18, 17)
        op(SpirvReflection.OP_TYPE_POINTER, 19, SpirvReflection.STORAGE_UNIFORM_CONSTANT, 18)
        op(SpirvReflection.OP_VARIABLE, 19, 20, SpirvReflection.STORAGE_UNIFORM_CONSTANT)
        op(SpirvReflection.OP_TYPE_POINTER, 21, SpirvReflection.STORAGE_INPUT, 3)
        op(SpirvReflection.OP_VARIABLE, 21, 22, SpirvReflection.STORAGE_INPUT)
        return words.toIntArray()
    }

    @Test
    fun Reflects_BlockMembersWithOffsetsStridesAndSize() {
        val r = SpirvReflection.of(module())
        val params = r.descriptors.first { it.binding == 0 }
        assertEquals("Params", params.name)
        assertEquals(SpirvReflection.DescriptorKind.UNIFORM_BUFFER, params.kind)
        assertEquals(
            listOf(
                SpirvReflection.Member("uMvp", 0, SpirvReflection.Scalar.FLOAT, 4, 4, 16),
                SpirvReflection.Member("uColor", 64, SpirvReflection.Scalar.FLOAT, 3),
                SpirvReflection.Member("uLights", 80, SpirvReflection.Scalar.INT, 1, count = 4, arrayStride = 16),
            ),
            params.members,
        )
        assertEquals(144, params.size)
    }

    @Test
    fun Reflects_SamplersWithCountsDimsAndDepth_AndVertexInputs() {
        val r = SpirvReflection.of(module())
        val textures = r.descriptors.first { it.binding == 2 }
        assertEquals("uTextures", textures.name)
        assertEquals(8, textures.count)
        assertEquals(SpirvReflection.ImageDim.D2, textures.dim)
        val shadow = r.descriptors.first { it.binding == 3 }
        assertEquals(SpirvReflection.ImageDim.D2_ARRAY, shadow.dim)
        assertEquals(true, shadow.depth)
        assertEquals(listOf(SpirvReflection.VertexInput(0, SpirvReflection.Scalar.FLOAT, 3)), r.vertexInputs)
    }

    @Test
    fun RejectsWhatIsNotSpirv() {
        assertThrows<IllegalArgumentException> { SpirvReflection.of(intArrayOf(1, 2, 3, 4, 5)) }
    }
}
