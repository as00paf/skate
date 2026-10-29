package com.pafoid.skate.engine.net

/**
 * Per-connection reliability on top of an unreliable datagram: packet sequence numbers with a 32-bit ack
 * bitfield, resent reliable-ordered messages and sequenced/unsequenced unreliable messages.
 * Pure state machine (no IO, caller supplies the clock) so it is fully unit-testable.
 *
 * Packet: `seq:u16 ack:u16 ackBits:u32` then messages `channel:u8 [msgId:u16 if reliable] len:varint data`.
 */
class ReliabilityLayer(
    private val resendMs: Long = 100,
    private val maxPacketSize: Int = 1200,
    private val maxPendingReliable: Int = 512,
) {
    private class Pending(val data: ByteArray, var lastSentMs: Long = Long.MIN_VALUE)
    private class Sent(val timeMs: Long, val reliableIds: List<Int>)

    private val unreliableOut = ArrayDeque<Pair<Channel, ByteArray>>()
    private val pendingReliable = LinkedHashMap<Int, Pending>()
    private val sentPackets = HashMap<Int, Sent>()
    private var nextMsgId = 0
    private var localSeq = 0

    private var hasRemote = false
    private var remoteSeq = 0
    private var ackBits = 0L
    private var ackDirty = false

    private var expectedMsgId = 0
    private val reorder = HashMap<Int, ByteArray>()
    private var lastSequencedSeq = -1
    private var lastSendMs = Long.MIN_VALUE

    var rttMs = 0f
        private set

    /** True when the peer stopped acknowledging; the owner should drop the connection. */
    val overflowed: Boolean get() = pendingReliable.size > maxPendingReliable

    fun send(channel: Channel, payload: ByteArray) {
        require(payload.size <= Transport.MAX_PAYLOAD) { "Payload ${payload.size} exceeds ${Transport.MAX_PAYLOAD}" }
        if (channel == Channel.RELIABLE_ORDERED) {
            pendingReliable[nextMsgId] = Pending(payload)
            nextMsgId = (nextMsgId + 1) and 0xFFFF
        } else {
            unreliableOut.addLast(channel to payload)
        }
    }

    /** Builds the datagrams to transmit now: due reliable messages, queued unreliable messages, acks. */
    fun flush(nowMs: Long): List<ByteArray> {
        val reliableDue = pendingReliable.entries.filter { it.value.lastSentMs == Long.MIN_VALUE || nowMs - it.value.lastSentMs >= resendMs }
        val keepAlive = hasRemote && (ackDirty || nowMs - lastSendMs >= KEEPALIVE_MS)
        if (reliableDue.isEmpty() && unreliableOut.isEmpty() && !keepAlive) return emptyList()

        val packets = ArrayList<ByteArray>()
        var w = newPacket()
        var count = 0
        var ids = ArrayList<Int>()
        fun finish() {
            packets += w.toByteArray()
            sentPackets[localSeq] = Sent(nowMs, ids)
            ids = ArrayList()
            localSeq = (localSeq + 1) and 0xFFFF
            w = newPacket()
            count = 0
        }
        fun fits(extra: Int) = w.size + extra <= maxPacketSize

        for ((id, p) in reliableDue) {
            if (!fits(p.data.size + 8) && count > 0) finish()
            w.writeByte(Channel.RELIABLE_ORDERED.id); w.writeShort(id); w.writeVarInt(p.data.size); w.writeBytes(p.data)
            p.lastSentMs = nowMs
            ids += id
            count++
        }
        while (unreliableOut.isNotEmpty()) {
            val (ch, data) = unreliableOut.first()
            if (!fits(data.size + 8) && count > 0) finish()
            unreliableOut.removeFirst()
            w.writeByte(ch.id); w.writeVarInt(data.size); w.writeBytes(data)
            count++
        }
        if (count > 0 || packets.isEmpty()) finish()
        ackDirty = false
        lastSendMs = nowMs
        sentPackets.keys.removeAll { newer(localSeq, it) && ((localSeq - it) and 0xFFFF) > 64 }
        return packets
    }

    private fun newPacket(): NetWriter = NetWriter(maxPacketSize).also {
        it.writeShort(localSeq); it.writeShort(remoteSeq); it.writeInt(ackBits.toInt())
    }

    /** Parses a datagram, delivering messages through [deliver]. Malformed packets are ignored. */
    fun receive(packet: ByteArray, nowMs: Long, deliver: (Channel, ByteArray) -> Unit) {
        if (packet.size < HEADER) return
        val r = NetReader(packet)
        val seq = r.readShort()
        val ack = r.readShort()
        val bits = r.readInt().toLong() and 0xFFFFFFFFL
        if (!registerReceived(seq)) return
        processAcks(ack, bits, nowMs)
        try {
            while (r.remaining > 0) {
                val ch = Channel.fromId(r.readByte()) ?: return
                when (ch) {
                    Channel.RELIABLE_ORDERED -> {
                        val id = r.readShort()
                        onReliable(id, r.readBytes(r.readVarInt()), deliver)
                    }
                    Channel.UNRELIABLE_SEQUENCED -> {
                        val data = r.readBytes(r.readVarInt())
                        if (lastSequencedSeq < 0 || newer(seq, lastSequencedSeq)) {
                            lastSequencedSeq = seq
                            deliver(ch, data)
                        }
                    }
                    Channel.UNRELIABLE -> deliver(ch, r.readBytes(r.readVarInt()))
                }
            }
        } catch (_: IllegalArgumentException) {
            // truncated message: drop the rest of this packet
        } catch (_: java.nio.BufferUnderflowException) {
        }
    }

    /** Returns false for duplicates/too-old packets. */
    private fun registerReceived(seq: Int): Boolean {
        ackDirty = true
        if (!hasRemote) {
            hasRemote = true; remoteSeq = seq; ackBits = 0
            return true
        }
        if (seq == remoteSeq) return false
        if (newer(seq, remoteSeq)) {
            val d = (seq - remoteSeq) and 0xFFFF
            ackBits = if (d >= 32) 0 else ((ackBits shl d) or (1L shl (d - 1))) and 0xFFFFFFFFL
            remoteSeq = seq
            return true
        }
        val d = (remoteSeq - seq) and 0xFFFF
        if (d > 32) return false
        val bit = 1L shl (d - 1)
        if (ackBits and bit != 0L) return false
        ackBits = ackBits or bit
        return true
    }

    private fun processAcks(ack: Int, bits: Long, nowMs: Long) {
        fun acked(seq: Int) {
            val sent = sentPackets.remove(seq) ?: return
            sent.reliableIds.forEach { pendingReliable.remove(it) }
            val sample = (nowMs - sent.timeMs).toFloat()
            rttMs = if (rttMs == 0f) sample else rttMs * 0.875f + sample * 0.125f
        }
        acked(ack)
        for (i in 0 until 32) if (bits and (1L shl i) != 0L) acked((ack - 1 - i) and 0xFFFF)
    }

    private fun onReliable(id: Int, data: ByteArray, deliver: (Channel, ByteArray) -> Unit) {
        val ahead = (id - expectedMsgId) and 0xFFFF
        if (ahead >= 0x8000) return // duplicate of an already delivered message
        if (ahead > maxPendingReliable) return
        if (ahead > 0) {
            reorder[id] = data
            return
        }
        deliver(Channel.RELIABLE_ORDERED, data)
        expectedMsgId = (expectedMsgId + 1) and 0xFFFF
        while (true) {
            val next = reorder.remove(expectedMsgId) ?: break
            deliver(Channel.RELIABLE_ORDERED, next)
            expectedMsgId = (expectedMsgId + 1) and 0xFFFF
        }
    }

    companion object {
        private const val HEADER = 8
        private const val KEEPALIVE_MS = 1000L

        /** True if [a] is more recent than [b] (16-bit serial arithmetic). */
        fun newer(a: Int, b: Int): Boolean {
            val d = (a - b) and 0xFFFF
            return d in 1..0x7FFF
        }
    }
}
