package com.pafoid.skate.engine.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PeerTransportTest {
    private val hub = FakePeerHub()
    private var now = 0L

    private class Recorder : TransportListener {
        val connected = ArrayList<Connection>()
        val disconnected = ArrayList<String>()
        val messages = ArrayList<String>()
        override fun onConnected(connection: Connection) { connected += connection }
        override fun onDisconnected(connection: Connection, reason: String) { disconnected += reason }
        override fun onMessage(connection: Connection, channel: Channel, payload: ByteArray) { messages += String(payload) }
    }

    private fun step(vararg pairs: Pair<Transport, TransportListener>, frames: Int = 1) = repeat(frames) {
        now += 16
        pairs.forEach { (t, l) -> t.poll(l, now) }
    }

    @Test
    fun PeerTransport_ConnectsByPeerIdAndExchangesReliableMessages() {
        val server = PeerTransport(hub.peer(1)); val client = PeerTransport(hub.peer(2))
        val s = Recorder(); val c = Recorder()
        server.listen(0); client.connect("1", 0)
        step(server to s, client to c, frames = 5)
        assertEquals(1, s.connected.size); assertEquals(1, c.connected.size)

        s.connected[0].send(Channel.RELIABLE_ORDERED, "from host".toByteArray())
        c.connected[0].send(Channel.RELIABLE_ORDERED, "from client".toByteArray())
        step(server to s, client to c, frames = 3)
        assertEquals(listOf("from client"), s.messages)
        assertEquals(listOf("from host"), c.messages)
    }

    @Test
    fun PeerTransport_ReliableOrderSurvivesPacketLoss() {
        var n = 0
        hub.dropFilter = { _, _, _ -> n++ % 3 == 2 }
        val server = PeerTransport(hub.peer(1)); val client = PeerTransport(hub.peer(2))
        val s = Recorder(); val c = Recorder()
        server.listen(0); client.connect("1", 0)
        step(server to s, client to c, frames = 20)
        val conn = c.connected.single()
        repeat(30) { conn.send(Channel.RELIABLE_ORDERED, "m$it".toByteArray()) }
        step(server to s, client to c, frames = 200)
        assertEquals((0 until 30).map { "m$it" }, s.messages)
    }

    @Test
    fun PeerTransport_NeverExceedsThePlatformDatagramSize() {
        val server = PeerTransport(hub.peer(1, maxDatagram = 600)); val client = PeerTransport(hub.peer(2, maxDatagram = 600))
        val s = Recorder(); val c = Recorder()
        server.listen(0); client.connect("1", 0)
        step(server to s, client to c, frames = 5)
        val payload = "x".repeat(500)
        repeat(10) { c.connected.single().send(Channel.RELIABLE_ORDERED, payload.toByteArray()) }
        step(server to s, client to c, frames = 10)
        assertEquals(10, s.messages.size)
        assertTrue(hub.sentSizes.all { it <= 600 }, "sizes: ${hub.sentSizes.max()}")
    }

    @Test
    fun PeerTransport_CloseNotifiesPeerAndReleasesPlatformSessions() {
        val hostPeer = hub.peer(1); val clientPeer = hub.peer(2)
        val server = PeerTransport(hostPeer); val client = PeerTransport(clientPeer)
        val s = Recorder(); val c = Recorder()
        server.listen(0); client.connect("1", 0)
        step(server to s, client to c, frames = 5)
        client.close()
        step(server to s, frames = 2)
        assertEquals(listOf("remote closed"), s.disconnected)
        assertEquals(listOf(1L), clientPeer.closed)
        assertEquals(listOf(2L), hostPeer.closed)
    }

    @Test
    fun PeerTransport_RejectsInvalidAddresses() {
        val t = PeerTransport(hub.peer(5))
        assertFailsWith<IllegalArgumentException> { t.connect("127.0.0.1", 0) }
        assertFailsWith<IllegalArgumentException> { t.connect("5", 0) }
    }

    @Test
    fun PeerTransport_ConnectToMissingPeerTimesOut() {
        val client = PeerTransport(hub.peer(2)); val c = Recorder()
        var failed: String? = null
        val l = object : TransportListener by c { override fun onConnectFailed(reason: String) { failed = reason } }
        client.connect("77", 0)
        step(client to l, frames = 400)
        assertEquals("timeout", failed)
    }
}
