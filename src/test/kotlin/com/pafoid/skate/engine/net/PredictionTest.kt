package com.pafoid.skate.engine.net

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PredictionTest {
    private val net = InMemoryNetwork()
    private val dt = 1f / 60f

    /** Input byte: -1/0/+1 along x at 5 m/s. */
    private val move = PredictionStep { go, input, step ->
        go.getComponent<Transform>()!!.translation.x += input[0] * 5f * step
    }

    private fun newObject(name: String) = GameObject(name).also { it.addComponent(Transform()); it.addComponent(NetState()) }
    private fun x(go: GameObject) = go.getComponent<Transform>()!!.translation.x

    private inner class World(serverStep: PredictionStep = move) {
        val server = ReplicationServer(net.createTransport())
        val clients = ArrayList<ReplicationClient>()
        private val serverStep = serverStep

        init {
            server.start(40000)
            server.onClientJoined = { cid, _ -> server.spawn(newObject("player$cid"), "player", cid) }
        }

        fun client(predict: Boolean = true): ReplicationClient =
            ReplicationClient(net.createTransport(), spawner = { _, _, prefab -> newObject(prefab) }, prediction = if (predict) move else null)
                .also { it.connect("local", 40000); clients += it }

        /** One 60 Hz frame; [inputs] per client (null = send nothing). */
        fun step(vararg inputs: Int?) {
            net.nowMs += 16
            clients.forEachIndexed { i, c ->
                c.poll(net.nowMs)
                inputs.getOrNull(i)?.let { c.sendInput(byteArrayOf(it.toByte())) }
            }
            server.poll(net.nowMs)
            server.processInputs { cid, payload -> server.ownedBy(cid).forEach { serverStep.step(it, payload, dt) } }
            server.tick()
            clients.forEach { it.poll(net.nowMs); it.update(dt) }
        }

        fun serverPlayer(c: ReplicationClient) = server.ownedBy(c.clientId).single()
    }

    @Test
    fun Prediction_LocalPlayerMovesOnTheSameFrameDespiteLatency() {
        net.latencyMs = 80
        val w = World()
        val predicted = w.client(predict = true)
        val plain = w.client(predict = false)
        repeat(40) { w.step(0, 0) }
        val p = assertNotNull(predicted.gameObject(predicted.predictedNetId))
        val plainId = w.serverPlayer(plain).getComponent<NetworkIdentity>()!!.netId
        val q = assertNotNull(plain.gameObject(plainId))

        w.step(1, 1)
        assertEquals(5f * dt, x(p), 1e-3f)  // predicted: moved immediately
        assertEquals(0f, x(q), 1e-6f)       // not predicted: waits for the server
        repeat(10) { w.step(1, 1) }
        assertEquals(0f, x(q), 1e-6f)       // still behind: RTT + interpolation delay
    }

    @Test
    fun Prediction_MatchesServerUnderLatencyAndLossWithoutVisibleCorrections() {
        net.latencyMs = 60
        var n = 0
        net.dropFilter = { _, _, _ -> n++ % 5 == 3 } // 20 % loss
        val w = World()
        val a = w.client(); val b = w.client()
        repeat(60) { w.step(0, 0) }
        val pa = assertNotNull(a.gameObject(a.predictedNetId))

        repeat(120) { w.step(1, 0) }
        repeat(30) { w.step(-1, 0) }
        repeat(90) { w.step(0, 0) }

        val serverX = x(w.serverPlayer(a))
        assertEquals(5f * dt * 90, serverX, 0.01f)          // every input applied once despite loss
        assertEquals(serverX, x(pa), 2f / 1024f)              // prediction converged on the server state
        assertEquals(0, a.predictor!!.correctionCount)        // no misprediction larger than 1 cm on the way
        assertTrue(a.predictor!!.pendingCount in 1..16)       // only the inputs still in flight (~RTT + snapshot interval)

        val remoteOnB = assertNotNull(b.gameObject(a.predictedNetId)) // b interpolates a's player
        assertEquals(serverX, x(remoteOnB), 2f / 1024f)
        assertNotEquals(a.predictedNetId, b.predictedNetId)
    }

    @Test
    fun Prediction_MispredictionIsCorrectedAndSmoothed() {
        net.latencyMs = 50
        // The server has a wall at x = 1 that the client does not know about.
        val walled = PredictionStep { go, input, step ->
            val t = go.getComponent<Transform>()!!.translation
            t.x = minOf(1f, t.x + input[0] * 5f * step)
        }
        val w = World(serverStep = walled)
        val c = w.client()
        repeat(40) { w.step(0) }
        val p = assertNotNull(c.gameObject(c.predictedNetId))
        repeat(30) { w.step(1) } // 2.5 m of input
        repeat(5) { w.step(0) }
        val pred = c.predictor!!
        assertTrue(pred.correctionCount > 0)
        assertTrue(pred.lastErrorDistance > 0f)
        repeat(60) { w.step(0) }
        assertEquals(1f, x(w.serverPlayer(c)), 1e-4f)
        assertEquals(1f, x(p), 0.01f) // converged, visual offset decayed
        assertTrue(abs(pred.correction[0]) < 0.01f)
    }

    @Test
    fun Predictor_ReplaysUnacknowledgedInputsAndSnapsLargeErrors() {
        val schema = ReplicationSchema()
        val pred = ClientPredictor(schema, move, dt, smoothingTimeS = 0.1f, snapDistance = 0.5f)
        val go = newObject("p")
        pred.attach(go, schema.capture(go))
        for (seq in 1..5) pred.predict(InputCommand(seq, byteArrayOf(1)))
        assertEquals(5, pred.pendingCount)

        // Server processed 3 inputs but put the player at x = 0.1 (e.g. it got pushed).
        val auth = schema.capture(newObject("s").also { it.getComponent<Transform>()!!.translation.x = 0.1f })
        pred.reconcile(3, auth)
        assertEquals(2, pred.pendingCount)
        val simX = Quantize.toFloat(pred.predictedState!![0], TransformReplicator.POS_SCALE)
        assertEquals(0.1f + 2 * 5f * dt, simX, 2f / 1024f)
        // old prediction was 5 steps = 0.4167; new is 0.2667: offset keeps the visual at the old place, then decays
        assertEquals(0.15f, pred.correction[0], 2f / 1024f)
        pred.render(0f)
        assertEquals(5 * 5f * dt, x(go), 2f / 1024f)
        pred.render(1f)
        assertEquals(simX, x(go), 2f / 1024f)

        pred.reconcile(2, schema.capture(newObject("old"))) // older ack: ignored
        assertEquals(2, pred.pendingCount)
        val far = schema.capture(newObject("far").also { it.getComponent<Transform>()!!.translation.x = 10f })
        pred.reconcile(5, far)
        assertEquals(0f, pred.correction[0]) // error > snapDistance: snap, no smoothing
        assertEquals(10f, Quantize.toFloat(pred.predictedState!![0], TransformReplicator.POS_SCALE), 1e-3f)
    }

    @Test
    fun ServerInputQueue_DropsDuplicatesAndSkipsWhenTooFarBehind() {
        val q = ServerInputQueue(maxBuffered = 3)
        q.receive(listOf(InputCommand(1, byteArrayOf(1), 32), InputCommand(2, byteArrayOf(2), 32)))
        q.receive(listOf(InputCommand(1, byteArrayOf(1)), InputCommand(2, byteArrayOf(2)), InputCommand(3, byteArrayOf(3), 48)))
        assertEquals(3, q.size)
        assertEquals(1, q.take()!!.seq)
        assertEquals(1, q.lastProcessedSeq); assertEquals(2.0, q.viewTick)
        q.receive((4..7).map { InputCommand(it, byteArrayOf(0)) })
        assertEquals(3, q.size)                 // 2, 3, 4 were dropped
        assertEquals(4, q.lastProcessedSeq)     // so the client stops replaying them
        assertEquals(listOf(5, 6, 7), List(3) { q.take()!!.seq })
        kotlin.test.assertNull(q.take())
    }

    @Test
    fun InputCodec_RoundTripsAndRejectsOversizedPayload() {
        val w = NetWriter(64)
        InputCodec.write(w, 1234, listOf(InputCommand(7, byteArrayOf(1, 2)), InputCommand(8, byteArrayOf())))
        val cmds = InputCodec.read(NetReader(w.toByteArray()))
        assertEquals(listOf(7, 8), cmds.map { it.seq })
        assertEquals(listOf<Byte>(1, 2), cmds[0].payload.toList())
        assertEquals(1234, cmds[1].viewTick16)

        val bad = NetWriter(16).also { it.writeVarInt(0); it.writeByte(1); it.writeVarInt(1); it.writeVarInt(5000) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { InputCodec.read(NetReader(bad.toByteArray())) }
    }
}
