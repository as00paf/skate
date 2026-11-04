package com.pafoid.skate.engine.platform

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNativeHandle
import com.codedisaster.steamworks.SteamNetworking
import com.codedisaster.steamworks.SteamNetworkingCallback
import com.pafoid.skate.engine.net.PeerNetwork
import com.pafoid.skate.engine.net.PeerPacket
import java.nio.ByteBuffer

/**
 * [PeerNetwork] over Steam P2P (ISteamNetworking; steamworks4j 1.9 has no ISteamNetworkingSockets). Packets are sent
 * unreliable on [CHANNEL]: reliability, ordering and the handshake are done by `DatagramTransport` above. Steam relays
 * traffic when a direct route is impossible.
 *
 * Incoming sessions are accepted only when [acceptSession] allows the peer (by default: members of the current lobby).
 */
internal class SteamPeerNetwork(
    override val localPeerId: Long,
    private val acceptSession: (peer: Long) -> Boolean,
) : PeerNetwork {
    private val callback: SteamNetworkingCallback = object : SteamNetworkingCallback {
        override fun onP2PSessionRequest(remote: SteamID) {
            if (acceptSession(SteamNativeHandle.getNativeHandle(remote))) networking.acceptP2PSessionWithUser(remote)
        }

        // The transport's connect timeout reports the failure to the game.
        override fun onP2PSessionConnectFail(remote: SteamID, error: SteamNetworking.P2PSessionError) {}
    }

    private val networking: SteamNetworking = SteamNetworking(callback).also { it.allowP2PPacketRelay(true) }
    private val sendBuffer: ByteBuffer = ByteBuffer.allocateDirect(maxDatagramSize)
    private val receiveBuffer: ByteBuffer = ByteBuffer.allocateDirect(MAX_RECEIVE)
    private val sizeOut = IntArray(1)
    private val sender = SteamID()

    override fun send(peer: Long, data: ByteArray): Boolean {
        if (data.size > maxDatagramSize) return false
        sendBuffer.clear(); sendBuffer.put(data); sendBuffer.flip()
        return networking.sendP2PPacket(SteamID.createFromNativeHandle(peer), sendBuffer, SteamNetworking.P2PSend.Unreliable, CHANNEL)
    }

    override fun receive(): List<PeerPacket> {
        val out = ArrayList<PeerPacket>()
        while (networking.isP2PPacketAvailable(CHANNEL, sizeOut)) {
            receiveBuffer.clear()
            val n = networking.readP2PPacket(sender, receiveBuffer, CHANNEL)
            if (n <= 0) break
            if (sizeOut[0] > MAX_RECEIVE) continue // larger than anything we send: truncated, drop it
            val bytes = ByteArray(n)
            receiveBuffer.position(0); receiveBuffer.get(bytes, 0, n)
            out += PeerPacket(SteamNativeHandle.getNativeHandle(sender), bytes)
        }
        return out
    }

    override fun closePeer(peer: Long) {
        networking.closeP2PSessionWithUser(SteamID.createFromNativeHandle(peer))
    }

    fun dispose() = networking.dispose()

    private companion object {
        const val CHANNEL = 0
        const val MAX_RECEIVE = 4096
    }
}
