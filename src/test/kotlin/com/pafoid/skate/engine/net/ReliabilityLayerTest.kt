package com.pafoid.skate.engine.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReliabilityLayerTest {
    private class Peer {
        val layer = ReliabilityLayer(resendMs = 50)
        val received = ArrayList<Pair<Channel, String>>()
        fun receive(p: ByteArray, now: Long) = layer.receive(p, now) { c, d -> received += c to String(d) }
    }

    private fun msg(s: String) = s.toByteArray()

    @Test
    fun Reliability_DeliversReliableMessagesDespiteLoss() {
        val a = Peer(); val b = Peer()
        for (i in 0 until 5) a.layer.send(Channel.RELIABLE_ORDERED, msg("m$i"))
        var now = 0L
        var dropped = 0
        repeat(40) {
            now += 20
            // drop every second datagram in both directions
            for (p in a.layer.flush(now)) if ((dropped++) % 2 == 0) b.receive(p, now)
            for (p in b.layer.flush(now)) if ((dropped++) % 2 == 0) a.receive(p, now)
        }
        assertEquals((0 until 5).map { Channel.RELIABLE_ORDERED to "m$it" }, b.received)
    }

    @Test
    fun Reliability_KeepsOrderAndDropsDuplicatesWhenReordered() {
        val a = Peer(); val b = Peer()
        a.layer.send(Channel.RELIABLE_ORDERED, msg("1")); val p1 = a.layer.flush(0)
        a.layer.send(Channel.RELIABLE_ORDERED, msg("2")); val p2 = a.layer.flush(0)
        b.receive(p2[0], 1); b.receive(p1[0], 2); b.receive(p1[0], 3)
        assertEquals(listOf("1", "2"), b.received.map { it.second })
    }

    @Test
    fun Reliability_StopsResendingOnceAcked() {
        val a = Peer(); val b = Peer()
        a.layer.send(Channel.RELIABLE_ORDERED, msg("x"))
        b.receive(a.layer.flush(0)[0], 1)
        a.receive(b.layer.flush(2)[0], 3)
        assertTrue(a.layer.flush(500).none { it.size > 8 }, "acked message must not be resent")
        assertTrue(a.layer.rttMs > 0f)
    }

    @Test
    fun Reliability_SequencedChannelDropsStalePackets() {
        val a = Peer(); val b = Peer()
        a.layer.send(Channel.UNRELIABLE_SEQUENCED, msg("old")); val old = a.layer.flush(0)
        a.layer.send(Channel.UNRELIABLE_SEQUENCED, msg("new")); val new = a.layer.flush(0)
        b.receive(new[0], 1); b.receive(old[0], 2)
        assertEquals(listOf("new"), b.received.map { it.second })
    }

    @Test
    fun Reliability_HandlesSequenceWrap() {
        val a = Peer(); val b = Peer()
        repeat(70000) { i ->
            a.layer.send(Channel.UNRELIABLE, msg("$i"))
            for (p in a.layer.flush(i.toLong())) b.receive(p, i.toLong())
        }
        assertEquals(70000, b.received.size)
        assertTrue(ReliabilityLayer.newer(2, 0xFFFE))
    }
}
