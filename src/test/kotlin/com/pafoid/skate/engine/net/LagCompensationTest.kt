package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LagCompensationTest {
    private fun newObject(name: String) = GameObject(name).also { it.addComponent(Transform()); it.addComponent(NetState()) }
    private fun x(go: GameObject) = go.getComponent<Transform>()!!.translation.x

    @Test
    fun StateHistory_BlendsBetweenTicksAndClampsToWindow() {
        val schema = ReplicationSchema()
        val h = StateHistory(schema, maxTicks = 4)
        for (tick in 1..6) h.record(tick, mapOf(1 to schema.capture(newObject("o").also { it.getComponent<Transform>()!!.translation.x = tick.toFloat() })))
        assertEquals(3, h.oldestTick); assertEquals(6, h.newestTick)
        fun at(t: Double) = Quantize.toFloat(h.stateAt(1, t)!![0], TransformReplicator.POS_SCALE)
        assertEquals(4.5f, at(4.5), 1e-3f)
        assertEquals(5f, at(5.0), 1e-3f)
        assertEquals(3f, at(1.0), 1e-3f) // older than the window: oldest kept
        assertEquals(6f, at(9.0), 1e-3f)
        assertNull(h.stateAt(2, 4.0))
    }

    @Test
    fun LagCompensation_RewindsOthersToTheClientViewAndRestoresExactly() {
        val net = InMemoryNetwork().also { it.latencyMs = 50 }
        val server = ReplicationServer(net.createTransport())
        server.start(40000)
        server.onClientJoined = { cid, _ -> server.spawn(newObject("player$cid"), "player", cid) }
        val target = newObject("target")
        server.spawn(target, "target")
        val client = ReplicationClient(net.createTransport(), spawner = { _, _, p -> newObject(p) })
        client.connect("local", 40000)

        val speed = 6f // m/s along x, so every server tick moves 0.1 m
        repeat(120) {
            net.nowMs += 16
            client.poll(net.nowMs); client.sendInput(byteArrayOf(0))
            server.poll(net.nowMs)
            server.processInputs { _, _ -> }
            target.getComponent<Transform>()!!.translation.x += speed / 60f
            server.tick()
            client.poll(net.nowMs); client.update(1f / 60f)
        }
        val cid = client.clientId
        val view = server.viewTickOf(cid)
        val behindTicks = server.currentTick - view
        // interpolation delay (6 ticks) + one-way latency (~3 ticks) + input queueing: well inside the 250 ms window
        assertTrue(behindTicks in 6.0..15.0, "behind $behindTicks ticks")

        val now = x(target)
        val own = server.ownedBy(cid).single()
        own.getComponent<Transform>()!!.translation.x = 42.123457f
        val seen = server.rewound(cid) { x(target) to x(own) }
        assertEquals(now - behindTicks.toFloat() * 0.1f, seen.first, 0.01f) // where the client saw it
        assertEquals(42.123457f, seen.second)                               // the shooter itself is not rewound
        assertEquals(now, x(target))                                       // restored bit-exact
    }

    @Test
    fun LagCompensation_ViewTickIsClampedToTheHistoryWindow() {
        val server = ReplicationServer(InMemoryNetwork().createTransport(), config = NetConfig(lagCompensationMs = 100))
        repeat(50) { server.tick() }
        assertEquals(7, server.stateHistory.maxTicks)
        assertEquals(50.0, server.viewTickOf(99)) // unknown client: present
    }
}
