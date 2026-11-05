package com.pafoid.skate.engine.render.rhi.opengl

/**
 * Location and last uploaded value of each named uniform of one program, so [GlShaderParams] uploads only values that
 * changed (docs/design/REN-12.md §3). Values compare bit for bit (so -0 differs from 0). GL-free for unit tests:
 * [locate] resolves each name once.
 */
internal class UniformCache(private val locate: (String) -> Int) {

    class Slot(val location: Int) {
        internal var bits = IntArray(4)
        internal var kind = UNSET
        internal var count = 0
    }

    private val slots = HashMap<String, Slot>()

    fun slot(name: String): Slot = slots.getOrPut(name) { Slot(locate(name)) }

    /**
     * True when the uniform exists and was not last set to the first [count] of [a]..[d]; records the value.
     * A uniform the program lacks (location -1) never needs an upload.
     */
    fun changed(slot: Slot, count: Int, a: Float, b: Float = 0f, c: Float = 0f, d: Float = 0f): Boolean {
        if (slot.location < 0) return false
        val bits = slot.bits
        var same = slot.kind == FLOATS && slot.count == count
        same = put(bits, 0, a.toRawBits(), same)
        if (count > 1) same = put(bits, 1, b.toRawBits(), same)
        if (count > 2) same = put(bits, 2, c.toRawBits(), same)
        if (count > 3) same = put(bits, 3, d.toRawBits(), same)
        return record(slot, FLOATS, count, same)
    }

    fun changed(slot: Slot, value: Int): Boolean {
        if (slot.location < 0) return false
        val same = put(slot.bits, 0, value, slot.kind == INTS && slot.count == 1)
        return record(slot, INTS, 1, same)
    }

    fun changed(slot: Slot, values: FloatArray): Boolean {
        if (slot.location < 0) return false
        if (slot.bits.size < values.size) slot.bits = IntArray(values.size).also { slot.kind = UNSET }
        var same = slot.kind == FLOATS && slot.count == values.size
        for (i in values.indices) same = put(slot.bits, i, values[i].toRawBits(), same)
        return record(slot, FLOATS, values.size, same)
    }

    fun changed(slot: Slot, values: IntArray): Boolean {
        if (slot.location < 0) return false
        if (slot.bits.size < values.size) slot.bits = IntArray(values.size).also { slot.kind = UNSET }
        var same = slot.kind == INTS && slot.count == values.size
        for (i in values.indices) same = put(slot.bits, i, values[i], same)
        return record(slot, INTS, values.size, same)
    }

    /** Stores [value] at [i]; returns whether everything matched so far. */
    private fun put(bits: IntArray, i: Int, value: Int, same: Boolean): Boolean {
        if (bits[i] == value) return same
        bits[i] = value
        return false
    }

    private fun record(slot: Slot, kind: Int, count: Int, same: Boolean): Boolean {
        slot.kind = kind
        slot.count = count
        return !same
    }

    private companion object {
        const val UNSET = 0
        const val FLOATS = 1
        const val INTS = 2
    }
}
