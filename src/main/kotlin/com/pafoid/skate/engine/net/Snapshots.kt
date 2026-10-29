package com.pafoid.skate.engine.net

/** Tunables approved in NET-01. */
data class NetConfig(
    val tickRate: Int = 60,
    val snapshotRate: Int = 20,
    val interpolationDelayMs: Int = 100,
    val maxPlayers: Int = 16,
    val protocolVersion: Int = 1,
) {
    init {
        require(snapshotRate in 1..tickRate && tickRate % snapshotRate == 0) { "tickRate must be a multiple of snapshotRate" }
    }

    val snapshotIntervalTicks: Int get() = tickRate / snapshotRate
    val interpolationDelayTicks: Double get() = interpolationDelayMs / 1000.0 * tickRate
}

internal object MessageType {
    const val HELLO = 1
    const val WELCOME = 2
    const val REJECT = 3
    const val SPAWN = 4
    const val DESPAWN = 5
    const val SNAPSHOT = 6
    const val SNAPSHOT_ACK = 7
    const val INPUT = 8
}

/** Per-object change relative to a baseline: [mask] bit i set means field i changed by [deltas] (in set-bit order). */
class ObjectDelta(val netId: Int, val mask: Long, val deltas: IntArray)

/** Deterministic delta encoding of object states. Netids are written in ascending order by callers. */
object SnapshotCodec {
    /** Returns null when nothing changed. A null [baseline] encodes every field against zero. */
    fun delta(netId: Int, current: IntArray, baseline: IntArray?): ObjectDelta? {
        var mask = 0L
        var n = 0
        for (i in current.indices) {
            if (baseline == null || current[i] != baseline[i]) { mask = mask or (1L shl i); n++ }
        }
        if (mask == 0L) return null
        val d = IntArray(n)
        var k = 0
        for (i in current.indices) if (mask and (1L shl i) != 0L) d[k++] = current[i] - (baseline?.get(i) ?: 0)
        return ObjectDelta(netId, mask, d)
    }

    fun writeObject(w: NetWriter, d: ObjectDelta) {
        w.writeVarInt(d.netId)
        w.writeVarLong(d.mask)
        d.deltas.forEach { w.writeZigZag(it) }
    }

    fun readObject(r: NetReader, fieldCount: Int): ObjectDelta {
        val id = r.readVarInt()
        val mask = r.readVarLong()
        require(mask ushr fieldCount == 0L) { "Malformed delta mask" }
        val d = IntArray(java.lang.Long.bitCount(mask)) { r.readZigZag() }
        return ObjectDelta(id, mask, d)
    }

    /** Applies [d] on top of [baseline] (zeros if null) and returns the new state; [baseline] is not modified. */
    fun apply(d: ObjectDelta, baseline: IntArray?, fieldCount: Int): IntArray {
        val out = baseline?.copyOf() ?: IntArray(fieldCount)
        var k = 0
        for (i in 0 until fieldCount) if (d.mask and (1L shl i) != 0L) out[i] += d.deltas[k++]
        return out
    }

    fun encodedSize(d: ObjectDelta): Int = NetWriter(32).also { writeObject(it, d) }.size
}
