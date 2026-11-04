package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.platform.LobbyInfo
import com.pafoid.skate.engine.platform.LobbyVisibility
import com.pafoid.skate.engine.platform.Lobbies
import com.pafoid.skate.engine.platform.NoOpPlatformServices
import com.pafoid.skate.engine.platform.PlatformServices

/** In-memory stand-in for Steam P2P and lobbies: no sockets, no natives. */
class FakePeerHub {
    val peers = HashMap<Long, FakePeer>()
    val lobbyData = HashMap<Long, MutableMap<String, String>>()
    val lobbyMembers = HashMap<Long, MutableSet<Long>>()
    var nextLobbyId = 9000L
    var failCreate = false

    /** Return true to drop a datagram. */
    var dropFilter: (from: Long, to: Long, data: ByteArray) -> Boolean = { _, _, _ -> false }
    val sentSizes = ArrayList<Int>()

    fun peer(id: Long, maxDatagram: Int = 1200) = FakePeer(this, id, maxDatagram).also { peers[id] = it }
}

class FakePeer(private val hub: FakePeerHub, override val localPeerId: Long, override val maxDatagramSize: Int) : PeerNetwork {
    val inbox = ArrayDeque<PeerPacket>()
    val closed = ArrayList<Long>()

    override fun send(peer: Long, data: ByteArray): Boolean {
        require(data.size <= maxDatagramSize) { "datagram ${data.size} > $maxDatagramSize" }
        hub.sentSizes += data.size
        if (hub.dropFilter(localPeerId, peer, data)) return true
        hub.peers[peer]?.inbox?.addLast(PeerPacket(localPeerId, data.copyOf()))
        return true
    }

    override fun receive(): List<PeerPacket> = inbox.toList().also { inbox.clear() }

    override fun closePeer(peer: Long) { closed += peer }
}

/** A user on the fake platform: real-looking lobbies whose callbacks are deferred to [update], like Steam. */
class FakePlatform(private val hub: FakePeerHub, val peer: FakePeer) : PlatformServices by NoOpPlatformServices("fake") {
    private val deferred = ArrayDeque<() -> Unit>()
    private var lobbyId: Long? = null
    var leaves = 0

    override val name = "Fake"
    override val isPlatformAvailable = true
    override val peerNetwork: PeerNetwork = peer

    private fun info(id: Long) = LobbyInfo(id, hub.lobbyMembers[id]?.size ?: 0, 0)

    override val lobbies = object : Lobbies {
        override val current: LobbyInfo? get() = lobbyId?.let { info(it) }

        override fun create(maxMembers: Int, visibility: LobbyVisibility, onResult: (LobbyInfo?) -> Unit) {
            deferred += {
                if (hub.failCreate) onResult(null) else {
                    val id = hub.nextLobbyId++
                    hub.lobbyData[id] = HashMap(); hub.lobbyMembers[id] = mutableSetOf(peer.localPeerId)
                    lobbyId = id
                    onResult(info(id))
                }
            }
        }

        override fun join(lobbyId: Long, onResult: (LobbyInfo?) -> Unit) {
            deferred += {
                val members = hub.lobbyMembers[lobbyId]
                if (members == null) onResult(null) else {
                    members += peer.localPeerId
                    this@FakePlatform.lobbyId = lobbyId
                    onResult(info(lobbyId))
                }
            }
        }

        override fun leave() {
            lobbyId?.let { hub.lobbyMembers[it]?.remove(peer.localPeerId) }
            lobbyId = null
            leaves++
        }

        override fun setData(key: String, value: String) = lobbyId?.let { hub.lobbyData[it]?.put(key, value); true } ?: false
        override fun getData(key: String) = lobbyId?.let { hub.lobbyData[it]?.get(key) }
        override fun list(onResult: (List<LobbyInfo>) -> Unit) { deferred += { onResult(hub.lobbyData.keys.map { info(it) }) } }
    }

    override fun update() {
        while (deferred.isNotEmpty()) deferred.removeFirst()()
    }
}
