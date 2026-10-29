package com.pafoid.skate.engine.net

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetCodecTest {
    @Test
    fun NetCodec_RoundTripsPrimitives() {
        val w = NetWriter(2)
        w.writeByte(200); w.writeBool(true); w.writeShort(65535); w.writeInt(-5); w.writeFloat(1.5f)
        w.writeVarInt(300); w.writeVarLong(1L shl 40); w.writeZigZag(-123456); w.writeString("héllo")
        val r = NetReader(w.toByteArray())
        assertEquals(200, r.readByte()); assertEquals(true, r.readBool()); assertEquals(65535, r.readShort())
        assertEquals(-5, r.readInt()); assertEquals(1.5f, r.readFloat()); assertEquals(300, r.readVarInt())
        assertEquals(1L shl 40, r.readVarLong()); assertEquals(-123456, r.readZigZag()); assertEquals("héllo", r.readString())
        assertEquals(0, r.remaining)
    }

    @Test
    fun NetCodec_ProducesStableBytes() {
        val w = NetWriter()
        w.writeVarInt(300); w.writeZigZag(-1); w.writeShort(0x0102)
        assertContentEquals(byteArrayOf(0xAC.toByte(), 0x02, 0x01, 0x02, 0x01), w.toByteArray())
    }

    @Test
    fun NetCodec_RejectsTruncatedData() {
        val r = NetReader(byteArrayOf(5, 1))
        kotlin.test.assertFailsWith<IllegalArgumentException> { r.readString() }
    }

    @Test
    fun SnapshotCodec_EncodesOnlyChangedFieldsAndRoundTrips() {
        val base = intArrayOf(10, 20, 30, 40)
        val cur = intArrayOf(10, 25, 30, 38)
        val d = SnapshotCodec.delta(7, cur, base)!!
        assertEquals(0b1010L, d.mask)
        assertContentEquals(intArrayOf(5, -2), d.deltas)

        val w = NetWriter(); SnapshotCodec.writeObject(w, d)
        val back = SnapshotCodec.readObject(NetReader(w.toByteArray()), 4)
        assertContentEquals(cur, SnapshotCodec.apply(back, base, 4))
        assertContentEquals(intArrayOf(10, 20, 30, 40), base)
    }

    @Test
    fun SnapshotCodec_UnchangedIsOmittedAndMissingBaselineIsFull() {
        val s = intArrayOf(1, 0, 3)
        assertNull(SnapshotCodec.delta(1, s, s.copyOf()))
        val full = SnapshotCodec.delta(1, s, null)!!
        assertEquals(0b111L, full.mask)
        assertContentEquals(s, SnapshotCodec.apply(full, null, 3))
    }

    @Test
    fun SnapshotCodec_DeltaIsSmallerThanFullState() {
        val base = IntArray(18) { it * 1000 }
        val cur = base.copyOf().also { it[0] += 3 }
        val full = SnapshotCodec.encodedSize(SnapshotCodec.delta(1, cur, null)!!)
        val delta = SnapshotCodec.encodedSize(SnapshotCodec.delta(1, cur, base)!!)
        assertTrue(delta < full / 3, "delta=$delta full=$full")
    }
}
