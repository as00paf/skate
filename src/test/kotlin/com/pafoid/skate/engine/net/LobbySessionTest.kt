package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.platform.LobbyVisibility
import com.pafoid.skate.engine.platform.NoOpPlatformServices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LobbySessionTest {
    private val hub = FakePeerHub()

    private fun ready(session: LobbySession) = assertIs<LobbySession.State.Ready>(session.state)

    @Test
    fun Lobby_WithoutPlatformHostsAndJoinsDirectly() {
        val net = InMemoryNetwork()
        val made = ArrayList<Transport>()
        val noSteam = NoOpPlatformServices("solo")
        val session = LobbySession(noSteam, directTransport = { net.createTransport().also { made += it } })
        assertTrue(!session.usesPlatform)

        session.host(4, directPort = 40100)
        val host = ready(session)
        assertEquals(LobbySession.Role.HOST, host.role); assertEquals(40100, host.port); assertNull(host.lobby)
        assertSame(made.single(), host.transport)

        val client = LobbySession(noSteam, directTransport = { net.createTransport() })
        client.join(123)
        assertIs<LobbySession.State.Failed>(client.state)
        client.joinDirect("local", 40100)
        val joined = ready(client)
        assertEquals(LobbySession.Role.CLIENT, joined.role); assertEquals("local", joined.address); assertEquals(40100, joined.port)

        val server = ReplicationServer(host.transport).also { it.start(host.port) }
        val rc = ReplicationClient(joined.transport, spawner = { _, _, _ -> null }).also { it.connect(joined.address, joined.port) }
        repeat(10) { net.nowMs += 16; server.poll(net.nowMs); server.tick(); rc.poll(net.nowMs) }
        assertEquals(ReplicationClient.State.CONNECTED, rc.state)
    }

    @Test
    fun Lobby_SteamFlowPublishesHostAndReplicatesOverPeerTransport() {
        val hostPlatform = FakePlatform(hub, hub.peer(100)); val clientPlatform = FakePlatform(hub, hub.peer(200))
        val hostSession = LobbySession(hostPlatform); val clientSession = LobbySession(clientPlatform)
        assertTrue(hostSession.usesPlatform)

        hostSession.host(4, LobbyVisibility.PUBLIC)
        assertIs<LobbySession.State.Creating>(hostSession.state)
        hostPlatform.update()
        val host = ready(hostSession)
        val lobbyId = assertNotNull(host.lobby).id
        assertEquals("100", hub.lobbyData[lobbyId]!![LobbySession.KEY_HOST])
        assertEquals(NetConfig().protocolVersion.toString(), hub.lobbyData[lobbyId]!![LobbySession.KEY_PROTOCOL])
        assertIs<PeerTransport>(host.transport)

        clientSession.join(lobbyId)
        assertIs<LobbySession.State.Joining>(clientSession.state)
        clientPlatform.update()
        val joined = ready(clientSession)
        assertEquals("100", joined.address)
        assertEquals(2, hub.lobbyMembers[lobbyId]!!.size)

        val server = ReplicationServer(host.transport).also { it.start(host.port) }
        val skater = GameObject("skater").also { it.addComponent(Transform()); it.addComponent(NetState()) }
        val netId = server.spawn(skater, "skater")
        val spawned = HashMap<Int, String>()
        val rc = ReplicationClient(joined.transport, spawner = { id, _, prefab ->
            spawned[id] = prefab
            GameObject(prefab).also { it.addComponent(Transform()); it.addComponent(NetState()) }
        }).also { it.connect(joined.address, joined.port, "remote") }
        var now = 0L
        repeat(20) { now += 16; server.poll(now); server.tick(); rc.poll(now); rc.update(1f / 60f) }
        assertEquals(ReplicationClient.State.CONNECTED, rc.state)
        assertEquals("skater", spawned[netId])
    }

    @Test
    fun Lobby_ProtocolMismatchFailsAndLeavesLobby() {
        val hostPlatform = FakePlatform(hub, hub.peer(1)); val clientPlatform = FakePlatform(hub, hub.peer(2))
        val hostSession = LobbySession(hostPlatform, protocolVersion = 7)
        hostSession.host(2); hostPlatform.update()
        val lobbyId = ready(hostSession).lobby!!.id

        val clientSession = LobbySession(clientPlatform, protocolVersion = 8)
        clientSession.join(lobbyId); clientPlatform.update()
        val failed = assertIs<LobbySession.State.Failed>(clientSession.state)
        assertTrue("protocol mismatch" in failed.reason, failed.reason)
        assertNull(clientPlatform.lobbies.current)
        assertEquals<Set<Long>?>(setOf(1L), hub.lobbyMembers[lobbyId])
    }

    @Test
    fun Lobby_CreateOrJoinFailureIsReported() {
        val platform = FakePlatform(hub, hub.peer(1))
        hub.failCreate = true
        val session = LobbySession(platform)
        session.host(2); platform.update()
        assertEquals<LobbySession.State>(LobbySession.State.Failed("lobby create failed"), session.state)

        session.join(424242); platform.update()
        assertEquals<LobbySession.State>(LobbySession.State.Failed("lobby join failed"), session.state)
    }

    @Test
    fun Lobby_LeaveDuringCreateIgnoresLateResultAndLeavesLobby() {
        val platform = FakePlatform(hub, hub.peer(1))
        val session = LobbySession(platform)
        session.host(2)
        session.leave()
        platform.update()
        assertIs<LobbySession.State.Idle>(session.state)
        assertNull(platform.lobbies.current)
        assertEquals(1, platform.leaves)
    }
}
