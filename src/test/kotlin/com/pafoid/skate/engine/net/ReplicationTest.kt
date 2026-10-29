package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplicationTest {
    private val net = InMemoryNetwork()
    private val cfg = NetConfig()
    private val server = ReplicationServer(net.createTransport(), config = cfg)

    private class TestClient(val client: ReplicationClient, val spawned: MutableMap<Int, GameObject>)

    private fun newObject(name: String): GameObject =
        GameObject(name).also { it.addComponent(Transform()); it.addComponent(NetState()) }

    private fun newClient(name: String = "c"): TestClient {
        val spawned = HashMap<Int, GameObject>()
        val client = ReplicationClient(net.createTransport(), config = cfg, spawner = { id, _, prefab ->
            newObject(prefab).also { spawned[id] = it }
        }, despawner = { go -> spawned.values.remove(go) })
        client.connect("local", 40000, name)
        return TestClient(client, spawned)
    }

    /** One 60 Hz step of the whole world. */
    private fun step(vararg clients: TestClient) {
        net.nowMs += 16
        server.poll(net.nowMs)
        server.tick()
        clients.forEach { it.client.poll(net.nowMs); it.client.update(1f / 60f) }
    }

    private fun run(steps: Int, vararg clients: TestClient) = repeat(steps) { step(*clients) }

    private fun x(go: GameObject) = go.getComponent<Transform>()!!.translation.x

    @Test
    fun Replication_TwoClientsSeeTheMovingObjectInterpolated() {
        server.start(40000)
        val skater = newObject("skater")
        val id = server.spawn(skater, "skater")
        val a = newClient("a"); val b = newClient("b")
        run(10, a, b)
        assertEquals(ReplicationClient.State.CONNECTED, a.client.state)
        assertNotNull(a.spawned[id]); assertNotNull(b.spawned[id])
        assertEquals(2, server.clientCount)

        repeat(120) {
            skater.getComponent<Transform>()!!.translation.x += 0.1f // 6 m/s
            skater.getComponent<NetState>()!!.values[0] = it
            step(a, b)
        }
        val serverX = x(skater)
        for (c in listOf(a, b)) {
            val cx = x(c.spawned.getValue(id))
            assertTrue(cx < serverX && cx > serverX - 2.0f, "client x=$cx server x=$serverX") // lags ~100-150 ms
            assertTrue(c.spawned.getValue(id).getComponent<NetState>()!!.values[0] > 100)
        }
        // smooth: consecutive frames differ by roughly the per-frame speed, no stepping at snapshot rate
        val before = x(a.spawned.getValue(id)); step(a, b); val after = x(a.spawned.getValue(id))
        assertEquals(0.1f, after - before, 0.04f)
    }

    @Test
    fun Replication_LateJoinerReceivesExistingObjectsAndDespawnIsReplicated() {
        server.start(40000)
        val o1 = newObject("one"); val o2 = newObject("two")
        o1.getComponent<Transform>()!!.translation.set(4f, 5f, 6f)
        val id1 = server.spawn(o1, "one"); val id2 = server.spawn(o2, "two")
        val late = newClient()
        run(30, late)
        assertEquals(setOf(id1, id2), late.spawned.keys)
        assertEquals(4f, x(late.spawned.getValue(id1)), 0.01f)

        server.despawn(id1)
        run(10, late)
        assertEquals(setOf(id2), late.spawned.keys)
        run(30, late) // later snapshots must not resurrect it
        assertNull(late.client.gameObject(id1))
    }

    @Test
    fun Replication_SurvivesPacketLossAndStaysConsistent() {
        server.start(40000)
        val o = newObject("o"); val id = server.spawn(o, "o")
        val c = newClient()
        var n = 0
        net.dropFilter = { _, _, _ -> (n++ % 3) == 1 }
        repeat(300) {
            o.getComponent<Transform>()!!.translation.x += 0.05f
            step(c)
        }
        assertEquals(ReplicationClient.State.CONNECTED, c.client.state)
        assertEquals(x(o), x(c.spawned.getValue(id)), 1.5f)
    }

    @Test
    fun Replication_RejectsProtocolMismatchAndFullServer() {
        val small = ReplicationServer(net.createTransport(), config = NetConfig(maxPlayers = 1))
        small.start(40010)
        val first = newClient()
        first.client.disconnect()
        val c1 = ReplicationClient(net.createTransport(), config = NetConfig(maxPlayers = 1), spawner = { _, _, _ -> null })
        val c2 = ReplicationClient(net.createTransport(), config = NetConfig(maxPlayers = 1), spawner = { _, _, _ -> null })
        c1.connect("l", 40010); c2.connect("l", 40010)
        repeat(30) { net.nowMs += 16; small.poll(net.nowMs); c1.poll(net.nowMs); c2.poll(net.nowMs) }
        assertEquals(1, small.clientCount)
        assertEquals(setOf(ReplicationClient.State.CONNECTED, ReplicationClient.State.REJECTED), setOf(c1.state, c2.state))

        server.start(40000)
        val old = ReplicationClient(net.createTransport(), config = NetConfig(protocolVersion = 99), spawner = { _, _, _ -> null })
        old.connect("l", 40000)
        repeat(30) { net.nowMs += 16; server.poll(net.nowMs); old.poll(net.nowMs) }
        assertEquals(ReplicationClient.State.REJECTED, old.state)
        assertTrue(old.failureReason!!.contains("protocol"))
    }

    @Test
    fun Replication_ForwardsClientInputToServer() {
        server.start(40000)
        val received = ArrayList<Triple<Int, Int, String>>()
        server.onInput = { cid, tick, payload -> received += Triple(cid, tick, String(payload)) }
        val c = newClient()
        run(10, c)
        c.client.sendInput(5, "push".toByteArray())
        run(5, c)
        assertEquals(listOf(Triple(c.client.clientId, 5, "push")), received)
    }

    @Test
    fun Replication_TimesOutSilentClient() {
        server.start(40000)
        val left = ArrayList<Int>()
        server.onClientLeft = { left += it }
        val c = newClient()
        run(10, c)
        repeat(700) { step() } // client stops polling
        assertEquals(1, left.size)
        assertEquals(0, server.clientCount)
    }

    @Test
    fun Replication_SplitsLargeSnapshotsIntoParts() {
        server.start(40000)
        val ids = (0 until 120).map { i ->
            newObject("o$i").also { it.getComponent<Transform>()!!.translation.set(i * 1.5f, i * 2.5f, -i * 3.5f) }
                .let { go -> server.spawn(go, "o$i") }
        }
        val c = newClient()
        run(60, c)
        assertEquals(ids.toSet(), c.spawned.keys)
        assertEquals(1.5f * 119, x(c.spawned.getValue(ids.last())), 0.05f)
    }
}
