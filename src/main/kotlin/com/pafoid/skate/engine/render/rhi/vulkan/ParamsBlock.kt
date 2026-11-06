package com.pafoid.skate.engine.render.rhi.vulkan

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CPU copy of a shader's std140 `Params` block (REN-13): named [com.pafoid.skate.engine.render.rhi.ShaderParams] writes
 * land at the offsets [SpirvReflection] found, converted to the member's scalar type, and each draw copies the block
 * into the frame's uniform ring. Unknown names are ignored, like a GL uniform at location -1.
 *
 * Names follow GL: `uName`, or `uName[i]` for element i of an array member. Pure Kotlin, unit-tested.
 */
class ParamsBlock(size: Int, members: List<SpirvReflection.Member>) {
    val data: ByteBuffer = ByteBuffer.allocate(maxOf(size, 16)).order(ByteOrder.LITTLE_ENDIAN)

    private val byName = members.associateBy { it.name }
    private class Slot(val member: SpirvReflection.Member, val offset: Int)
    private val slots = HashMap<String, Slot?>()

    private fun slot(name: String): Slot? = slots.getOrPut(name) {
        val bracket = name.indexOf('[')
        if (bracket < 0) byName[name]?.let { Slot(it, it.offset) }
        else {
            val member = byName[name.substring(0, bracket)] ?: return@getOrPut null
            val index = name.substring(bracket + 1, name.indexOf(']', bracket)).trim().toIntOrNull() ?: return@getOrPut null
            if (index !in 0 until member.count) null else Slot(member, member.offset + index * member.arrayStride)
        }
    }

    private fun put(offset: Int, scalar: SpirvReflection.Scalar, value: Float) {
        if (scalar == SpirvReflection.Scalar.FLOAT) data.putFloat(offset, value) else data.putInt(offset, value.toInt())
    }

    private fun put(offset: Int, scalar: SpirvReflection.Scalar, value: Int) {
        if (scalar == SpirvReflection.Scalar.FLOAT) data.putFloat(offset, value.toFloat()) else data.putInt(offset, value)
    }

    fun set(name: String, value: Float) {
        val s = slot(name) ?: return
        put(s.offset, s.member.scalar, value)
    }

    fun set(name: String, value: Int) {
        val s = slot(name) ?: return
        put(s.offset, s.member.scalar, value)
    }

    /** First [count] components of a vector. */
    fun set(name: String, count: Int, x: Float, y: Float, z: Float, w: Float) {
        val s = slot(name) ?: return
        val n = minOf(count, s.member.components)
        val scalar = s.member.scalar
        put(s.offset, scalar, x)
        if (n > 1) put(s.offset + 4, scalar, y)
        if (n > 2) put(s.offset + 8, scalar, z)
        if (n > 3) put(s.offset + 12, scalar, w)
    }

    /** Consecutive elements of an int (or bool) array starting at [name]'s element. */
    fun set(name: String, values: IntArray) {
        val s = slot(name) ?: return
        val stride = if (s.member.arrayStride > 0) s.member.arrayStride else 4
        val room = if (s.member.count > 1) s.member.count - (s.offset - s.member.offset) / stride else 1
        for (i in 0 until minOf(values.size, room)) put(s.offset + i * stride, s.member.scalar, values[i])
    }

    /** Column-major 4x4 matrices: one, or consecutive array elements starting at [name]'s element. */
    fun setMatrix4(name: String, values: FloatArray) {
        val s = slot(name) ?: return
        val m = s.member
        if (m.columns != 4 || m.components != 4) return
        val matrices = values.size / 16
        val stride = if (m.arrayStride > 0) m.arrayStride else m.matrixStride * 4
        val room = if (m.count > 1) m.count - (s.offset - m.offset) / stride else 1
        for (k in 0 until minOf(matrices, room)) {
            val base = s.offset + k * stride
            for (c in 0 until 4) for (r in 0 until 4) data.putFloat(base + c * m.matrixStride + r * 4, values[k * 16 + c * 4 + r])
        }
    }
}
