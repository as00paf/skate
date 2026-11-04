package com.pafoid.skate.engine.net

class PeerPacket(val peer: Long, val data: ByteArray)

/**
 * Peer-addressed datagram service from a platform (Steam P2P). Peers are platform user ids, packets may be lost,
 * duplicated or reordered, and nothing here touches sockets directly. Called from the main thread only.
 */
interface PeerNetwork {
    /** The local user's peer id (e.g. the SteamID). */
    val localPeerId: Long

    /** Largest datagram [send] accepts. */
    val maxDatagramSize: Int get() = 1200

    /** Sends one unreliable datagram; false if the platform refused it. */
    fun send(peer: Long, data: ByteArray): Boolean

    /** Returns every datagram received since the last call. */
    fun receive(): List<PeerPacket>

    /** Releases platform session resources for [peer]. */
    fun closePeer(peer: Long)
}

/**
 * NET-02 [Transport] over a [PeerNetwork]. Handshake, reliability and timeouts come from [DatagramTransport], so the
 * wire format inside each platform datagram is the same as over UDP.
 *
 * Addressing: [listen] ignores the port (peers are reached by id); [connect] takes the host's peer id as a decimal
 * string in `host` and ignores the port.
 */
class PeerTransport(
    private val network: PeerNetwork,
    maxConnections: Int = 16,
) : DatagramTransport(maxConnections = maxConnections, maxDatagramSize = network.maxDatagramSize) {
    private var open = false

    override fun openServer(port: Int) { open = true }

    override fun openClient(host: String, port: Int): Any {
        val peer = requireNotNull(host.toLongOrNull()) { "PeerTransport.connect expects a peer id, got '$host'" }
        require(peer != network.localPeerId) { "Cannot connect to self" }
        open = true
        return peer
    }

    override fun sendRaw(remote: Any, data: ByteArray) {
        if (open && data.size <= network.maxDatagramSize) network.send(remote as Long, data)
    }

    override fun drain(): List<Datagram> =
        if (!open) emptyList() else network.receive().map { Datagram(it.peer, it.data) }

    override fun onRemoteDropped(remote: Any) = network.closePeer(remote as Long)

    override fun closeSocket() { open = false }
}
