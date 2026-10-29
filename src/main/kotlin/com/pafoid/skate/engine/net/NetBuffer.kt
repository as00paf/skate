package com.pafoid.skate.engine.net

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Deterministic little-endian binary writer. Same calls always produce the same bytes. */
class NetWriter(initialCapacity: Int = 256) {
    private var buf: ByteBuffer = ByteBuffer.allocate(initialCapacity).order(ByteOrder.LITTLE_ENDIAN)

    val size: Int get() = buf.position()

    private fun ensure(extra: Int) {
        if (buf.remaining() >= extra) return
        val bigger = ByteBuffer.allocate(maxOf(buf.capacity() * 2, buf.position() + extra)).order(ByteOrder.LITTLE_ENDIAN)
        buf.flip()
        bigger.put(buf)
        buf = bigger
    }

    fun writeByte(v: Int) { ensure(1); buf.put(v.toByte()) }
    fun writeBool(v: Boolean) = writeByte(if (v) 1 else 0)
    fun writeShort(v: Int) { ensure(2); buf.putShort(v.toShort()) }
    fun writeInt(v: Int) { ensure(4); buf.putInt(v) }
    fun writeFloat(v: Float) { ensure(4); buf.putFloat(v) }

    fun writeBytes(bytes: ByteArray) { ensure(bytes.size); buf.put(bytes) }

    /** Unsigned LEB128; negative values are written as 5 bytes (use [writeZigZag] for signed data). */
    fun writeVarInt(value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        writeByte(v)
    }

    fun writeVarLong(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            writeByte(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        writeByte(v.toInt())
    }

    fun writeZigZag(value: Int) = writeVarInt((value shl 1) xor (value shr 31))

    fun writeString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeVarInt(bytes.size)
        writeBytes(bytes)
    }

    fun toByteArray(): ByteArray = ByteArray(buf.position()).also { buf.duplicate().flip().get(it) }
}

class NetReader(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
    private val buf: ByteBuffer = ByteBuffer.wrap(data, offset, length).slice().order(ByteOrder.LITTLE_ENDIAN)

    val remaining: Int get() = buf.remaining()

    fun readByte(): Int = buf.get().toInt() and 0xFF
    fun readBool(): Boolean = readByte() != 0
    fun readShort(): Int = buf.short.toInt() and 0xFFFF
    fun readInt(): Int = buf.int
    fun readFloat(): Float = buf.float

    fun readBytes(n: Int): ByteArray {
        require(n in 0..buf.remaining()) { "Malformed packet: wants $n bytes, ${buf.remaining()} left" }
        return ByteArray(n).also { buf.get(it) }
    }

    fun readVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            require(shift < 35) { "Malformed varint" }
            val b = readByte()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun readVarLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(shift < 70) { "Malformed varlong" }
            val b = readByte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun readZigZag(): Int {
        val v = readVarInt()
        return (v ushr 1) xor -(v and 1)
    }

    fun readString(): String = String(readBytes(readVarInt()), Charsets.UTF_8)
}

object Quantize {
    fun toInt(v: Float, scale: Float): Int = Math.round(v * scale)
    fun toFloat(q: Int, scale: Float): Float = q / scale
}
