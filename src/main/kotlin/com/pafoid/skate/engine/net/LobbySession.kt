package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.platform.LobbyInfo
import com.pafoid.skate.engine.platform.LobbyVisibility
import com.pafoid.skate.engine.platform.PlatformServices

/**
 * Lobby flow on top of [PlatformServices]: picks the transport and the address a [ReplicationServer] listens on or a
 * [ReplicationClient] connects to.
 *
 * - Platform with lobbies and a [PeerNetwork] (Steam): [host] creates a lobby and publishes the host's peer id and
 *   protocol version as lobby data; [join] enters a lobby, checks the protocol and connects to that peer over
 *   [PeerTransport].
 * - Otherwise (no Steam): [host] is ready at once with a direct transport (UDP) on the given port, and clients use
 *   [joinDirect] with an IP and port. [join] fails because there are no lobbies to join.
 *
 * Platform callbacks arrive from [PlatformServices.update]. The transport in [State.Ready] belongs to the
 * replication object that uses it, which closes it on stop/disconnect.
 */
class LobbySession(
    private val platform: PlatformServices,
    private val protocolVersion: Int = NetConfig().protocolVersion,
    private val directTransport: () -> Transport = { UdpTransport() },
    private val peerTransport: (PeerNetwork) -> Transport = { PeerTransport(it) },
) {
    enum class Role { HOST, CLIENT }

    sealed interface State {
        object Idle : State
        object Creating : State
        object Joining : State

        /**
         * Hosts call `ReplicationServer(transport).start(port)`; clients call
         * `ReplicationClient(transport, ...).connect(address, port)`. [lobby] is null in direct (no platform) mode.
         */
        data class Ready(val role: Role, val transport: Transport, val address: String, val port: Int, val lobby: LobbyInfo?) : State

        data class Failed(val reason: String) : State
    }

    var state: State = State.Idle
        private set

    /** True when lobbies and peer networking come from the platform (Steam). */
    val usesPlatform: Boolean get() = platform.isPlatformAvailable && platform.peerNetwork != null

    private var attempt = 0

    fun host(maxPlayers: Int, visibility: LobbyVisibility = LobbyVisibility.FRIENDS_ONLY, directPort: Int = DEFAULT_PORT) {
        check(state !is State.Creating && state !is State.Joining) { "Lobby operation already in progress" }
        val peers = platform.peerNetwork
        if (!usesPlatform || peers == null) {
            state = State.Ready(Role.HOST, directTransport(), "", directPort, null)
            return
        }
        state = State.Creating
        val token = ++attempt
        platform.lobbies.create(maxPlayers, visibility) { lobby ->
            if (token != attempt) { if (lobby != null) platform.lobbies.leave(); return@create }
            state = if (lobby == null) State.Failed("lobby create failed") else {
                platform.lobbies.setData(KEY_HOST, peers.localPeerId.toString())
                platform.lobbies.setData(KEY_PROTOCOL, protocolVersion.toString())
                State.Ready(Role.HOST, peerTransport(peers), peers.localPeerId.toString(), 0, lobby)
            }
        }
    }

    fun join(lobbyId: Long) {
        check(state !is State.Creating && state !is State.Joining) { "Lobby operation already in progress" }
        val peers = platform.peerNetwork
        if (!usesPlatform || peers == null) {
            state = State.Failed("lobbies unavailable without a platform; use joinDirect")
            return
        }
        state = State.Joining
        val token = ++attempt
        platform.lobbies.join(lobbyId) { lobby ->
            if (token != attempt) { if (lobby != null) platform.lobbies.leave(); return@join }
            state = if (lobby == null) State.Failed("lobby join failed") else readyAsClient(peers, lobby)
        }
    }

    private fun readyAsClient(peers: PeerNetwork, lobby: LobbyInfo): State {
        val protocol = platform.lobbies.getData(KEY_PROTOCOL)?.toIntOrNull()
        val host = platform.lobbies.getData(KEY_HOST)?.toLongOrNull()
        val failure = when {
            protocol != protocolVersion -> "protocol mismatch (lobby $protocol, client $protocolVersion)"
            host == null -> "lobby has no host"
            host == peers.localPeerId -> "already the host of this lobby"
            else -> null
        }
        if (failure != null) {
            platform.lobbies.leave()
            return State.Failed(failure)
        }
        return State.Ready(Role.CLIENT, peerTransport(peers), host.toString(), 0, lobby)
    }

    /** Connects to a host by address over the direct transport; works with and without a platform. */
    fun joinDirect(host: String, port: Int = DEFAULT_PORT) {
        check(state !is State.Creating && state !is State.Joining) { "Lobby operation already in progress" }
        attempt++
        state = State.Ready(Role.CLIENT, directTransport(), host, port, null)
    }

    /** Leaves the lobby (if any) and cancels a pending create/join. Does not close the transport. */
    fun leave() {
        attempt++
        if (platform.lobbies.current != null) platform.lobbies.leave()
        state = State.Idle
    }

    companion object {
        const val DEFAULT_PORT = 27_015
        const val KEY_HOST = "skate_host_peer"
        const val KEY_PROTOCOL = "skate_protocol"
    }
}
